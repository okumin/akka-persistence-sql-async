package akka.persistence.journal.sqlasync

import akka.actor.ActorLogging
import akka.persistence.common.{ScalikeJDBCExtension, ScalikeJDBCSessionProvider}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}
import scala.collection.immutable
import scala.collection.mutable.ListBuffer
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
    log.debug("Write messages, {}", messages)
    val batch = ListBuffer.empty[SQLSyntax]
    val result = messages.map { writes =>
      writes.payload.foldLeft[Try[List[SQLSyntax]]](Success(Nil)) {
        case (Success(xs), x) => serialization.serialize(x) match {
          case Success(bytes) => Success(sqls"(${x.persistenceId}, ${x.sequenceNr}, $bytes)" :: xs)
          case Failure(e) => Failure(e)
        }
        case (Failure(e), _) => Failure(e)
      }.map(_.reverse).map(batch.append)
    }

    val records = sqls.csv(batch: _*)
    val sql = sql"INSERT INTO $table (persistence_id, sequence_nr, message) VALUES $records"
    log.debug("Execute {}, binding {}", sql.statement, messages)
    sessionProvider.localTx { implicit session =>
      sql.update().future().map(_ => result)
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
          val message = serialization.deserialize(bytes, classOf[PersistentRepr]).get
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
}
