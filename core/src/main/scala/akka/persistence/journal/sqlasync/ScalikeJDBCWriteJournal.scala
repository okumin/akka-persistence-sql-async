package akka.persistence.journal.sqlasync

import akka.actor.ActorLogging
import akka.persistence.common.{ScalikeJDBCExtension, ScalikeJDBCSessionProvider}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scalikejdbc._
import scalikejdbc.async._

private[sqlasync] trait ScalikeJDBCWriteJournal extends AsyncWriteJournal with ActorLogging {
  import context.dispatcher
  private[this] val serialization: Serialization = SerializationExtension(context.system)
  private[this] lazy val extension: ScalikeJDBCExtension = ScalikeJDBCExtension(context.system)
  private[this] lazy val sessionProvider: ScalikeJDBCSessionProvider = extension.sessionProvider
  private[this] lazy val table = {
    val tableName = extension.config.journalTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    def traverse[A, B](xs: Seq[A])(f: A => Future[B]): Future[Unit] = {
      xs.foldLeft(Future.successful(())) { (result, x) =>
        for {
          a <- result
          b <- f(x)
        } yield ()
      }
    }

    log.debug("Write messages, {}", messages)
    // TODO: bulk insert
    sessionProvider.localTx { implicit session =>
      messages.foldLeft(Future.successful[List[Try[Unit]]](Nil)) { (result, write) =>
        result.flatMap { rejects =>
          Try(write.payload.map { p => (p, persistenceToBytes(p) )}) match {
            case Failure(e) => Future.successful(Failure(e) :: rejects)
            case Success(payloads) =>
              traverse(payloads) {
                case (payload, bytes) =>
                  val sql = sql"INSERT INTO $table (persistence_id, sequence_nr, message) VALUES (${payload.persistenceId}, ${payload.sequenceNr}, $bytes)"
                  log.debug("Execute {}, binding {}", sql.statement, payload)
                  sql.update().future()
              }.map(_ => Success(()) :: rejects)
          }
        }
      }.map(_.reverse)
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    log.debug("Delete messages, persistenceId = {}, toSequenceNr = {}", persistenceId, toSequenceNr)
    sessionProvider.localTx { implicit session =>
      val sql = sql"DELETE FROM $table WHERE persistence_id = $persistenceId AND sequence_nr <= $toSequenceNr"
      log.debug("Execute {}, binding persistence_id = {} and sequence_nr = {}", sql.statement, persistenceId, toSequenceNr)
      sql.update().future().map(_ => ())
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug("Replay messages, persistenceId = {}, fromSequenceNr = {}, toSequenceNr = {}", persistenceId, fromSequenceNr, toSequenceNr)
    sessionProvider.withPool { implicit session =>
      val sql = sql"SELECT message FROM $table WHERE persistence_id = $persistenceId AND sequence_nr >= $fromSequenceNr AND sequence_nr <= $toSequenceNr ORDER BY sequence_nr ASC LIMIT $max"
      log.debug("Execute {}, binding persistence_id = {}, from_sequence_nr = {}, to_sequence_nr = {}", sql.statement, persistenceId, fromSequenceNr, toSequenceNr)
      sql.map(_.bytes("message")).list().future().map { messages =>
        messages.foreach { bytes =>
          val message = persistenceFromBytes(bytes)
          replayCallback(message)
        }
      }.map(_ => ())
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug("Read the highest sequence number, persistenceId = {}, fromSequenceNr = {}", persistenceId, fromSequenceNr)
    sessionProvider.withPool { implicit session =>
      val sql = sql"SELECT sequence_nr FROM $table WHERE persistence_id = $persistenceId ORDER BY sequence_nr DESC LIMIT 1"
      log.debug("Execute {} binding persistence_id = {} and sequence_nr = {}", sql.statement, persistenceId, fromSequenceNr)
      sql.map(_.longOpt(1)).single().future().map(_.flatten.getOrElse(0L))
    }
  }

  private[this] def persistenceToBytes(repr: PersistentRepr): Array[Byte] = {
    serialization.serialize(repr).get
  }
  private[this] def persistenceFromBytes(bytes: Array[Byte]): PersistentRepr = {
    serialization.deserialize(bytes, classOf[PersistentRepr]).get
  }
}
