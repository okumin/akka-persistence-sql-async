package akka.persistence.journal.sqlasync

import akka.persistence.common.StoragePlugin
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import scalikejdbc._
import scalikejdbc.async._

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Success, Try}

private[sqlasync] trait ScalikeJDBCWriteJournal extends AsyncWriteJournal with StoragePlugin {
  private[this] lazy val journalTable = {
    val tableName = extension.config.journalTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)(implicit session: TxAsyncDBSession): Future[Unit]

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    def serialize(keys: Map[String, Long]): (SQLSyntax, immutable.Seq[Try[Unit]]) = {
      val result = messages.map { write =>
        write.payload.foldLeft(Try(Vector.empty[SQLSyntax])) { (acc, x) =>
          for {
            a <- acc
            bytes <- serialization.serialize(x)
          } yield a :+ sqls"(${keys(x.persistenceId)}, ${x.sequenceNr}, $bytes)"
        }
      }
      val batch = result.collect {
        case Success(x) => x
      }.flatten
      (sqls.csv(batch: _*), result.map(_.map(_ => ())))
    }

    log.debug("Write messages, {}", messages)
    if (messages.isEmpty) {
      Future.successful(Nil)
    } else {
      sessionProvider.localTx { implicit session =>
        val persistenceIds = messages.map(_.persistenceId).toSet

        for {
          keys <- persistenceIds.foldLeft(Future.successful(Map.empty[String, Long])) { (acc, id) =>
            for {
              map <- acc
              persistenceKey <- surrogateKeyOf(id)
            } yield map.updated(id, persistenceKey)
          }
          (batch, result) = serialize(keys)
          sql = sql"INSERT INTO $journalTable (persistence_key, sequence_nr, message) VALUES $batch"
          _ <- logging(sql).update().future()
        } yield result
      }
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    log.debug("Delete messages, persistenceId = {}, toSequenceNr = {}", persistenceId, toSequenceNr)
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        select = sql"SELECT sequence_nr FROM $journalTable WHERE persistence_key = $key ORDER BY sequence_nr DESC LIMIT 1"
        highest <- logging(select).map(_.long("sequence_nr")).single().future().map(_.getOrElse(0L))
        delete = sql"DELETE FROM $journalTable WHERE persistence_key = $key AND sequence_nr <= $toSequenceNr"
        _ <- logging(delete).update().future()
        _ <- if (highest <= toSequenceNr) updateSequenceNr(persistenceId, highest) else Future.successful(())
      } yield ()
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug("Replay messages, persistenceId = {}, fromSequenceNr = {}, toSequenceNr = {}", persistenceId, fromSequenceNr, toSequenceNr)
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"SELECT message FROM $journalTable WHERE persistence_key = $key AND sequence_nr >= $fromSequenceNr AND sequence_nr <= $toSequenceNr ORDER BY sequence_nr ASC LIMIT $max"
        _ <- logging(sql).map(_.bytes("message")).list().future().map { messages =>
          messages.foreach { bytes =>
            val message = serialization.deserialize(bytes, classOf[PersistentRepr]).get
            replayCallback(message)
          }
        }
      } yield ()
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug("Read the highest sequence number, persistenceId = {}, fromSequenceNr = {}", persistenceId, fromSequenceNr)
    sessionProvider.localTx { implicit session =>
      val fromMetadataTable = sql"SELECT persistence_key, sequence_nr FROM $metadataTable WHERE persistence_id = $persistenceId"
      logging(fromMetadataTable).map { result =>
        (result.long("persistence_key"), result.long("sequence_nr"))
      }.single().future().flatMap {
        case None => Future.successful(fromSequenceNr) // No persistent record exists.
        case Some((key, fromMetadata)) =>
          val fromJournalTable = sql"SELECT sequence_nr FROM $journalTable WHERE persistence_key = $key ORDER BY sequence_nr DESC LIMIT 1"
          logging(fromJournalTable).map(_.long("sequence_nr")).single().future().map {
            case Some(fromJournal) => fromJournal max fromMetadata max fromSequenceNr
            case None => fromMetadata max fromSequenceNr
          }
      }
    }
  }
}
