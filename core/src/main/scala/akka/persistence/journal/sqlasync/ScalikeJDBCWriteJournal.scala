package akka.persistence.journal.sqlasync

import akka.actor.ActorLogging
import akka.persistence.common.{ScalikeJDBCExtension, ScalikeJDBCSessionProvider}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.journal.sqlasync.ScalikeJDBCWriteJournal._
import akka.persistence.{PersistentConfirmation, PersistentId, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}
import scala.collection.immutable
import scala.concurrent.Future
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

  override def asyncWriteMessages(messages: immutable.Seq[PersistentRepr]): Future[Unit] = {
    log.debug(s"Write messages, $messages")
    // TODO: bulk insert
    sessionProvider.localTx { implicit session =>
      messages.foldLeft(Future.successful(())) { (result, message) =>
        result.flatMap { _ =>
          val bytes = persistenceToBytes(message)
          val sql = sql"INSERT INTO $table (persistence_id, sequence_nr, marker, message, created_at) VALUES (${message.persistenceId}, ${message.sequenceNr}, $AcceptedMarker, $bytes, CURRENT_TIMESTAMP)"
          log.debug(s"Execute ${sql.statement}, binding $message")
          sql.update().future().map(_ => ())
        }
      }
    }
  }

  @deprecated("writeConfirmations will be removed, since Channels will be removed.")
  override def asyncWriteConfirmations(confirmations: immutable.Seq[PersistentConfirmation]): Future[Unit] = {
    log.debug(s"Write confirmations, $confirmations")
    def select(persistenceId: String, sequenceNr: Long)(implicit session: AsyncDBSession): Future[Option[PersistentRepr]] = {
      val sql = sql"SELECT marker, message FROM $table WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr"
      log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId, sequence_nr = $sequenceNr")
      sql.map(r => (r.string("marker"), r.bytes("message"))).single().future().map(_.flatMap {
        case (DeletedMarker, message) => None
        case (_, message) => Some(persistenceFromBytes(message))
      })
    }
    def update(message: PersistentRepr)(implicit session: AsyncDBSession): Future[Unit] = {
      val persistenceId = message.persistenceId
      val sequenceNr = message.sequenceNr
      val bytes = persistenceToBytes(message)
      val sql = sql"UPDATE $table SET message = $bytes WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr"
      log.debug(s"Execute ${sql.statement}, binding $message")
      sql.update().future().map(_ => ())
    }

    sessionProvider.localTx { implicit session =>
      confirmations.foldLeft(Future.successful(())) { (result, confirmation) =>
        result.flatMap { _ =>
          select(confirmation.persistenceId, confirmation.sequenceNr).flatMap {
            case Some(message) =>
              val confirmationIds = message.confirms :+ confirmation.channelId
              val newMessage = message.update(confirms = confirmationIds)
              update(newMessage)
            case None => Future.successful(())
          }
        }
      }
    }
  }

  @deprecated("asyncDeleteMessages will be removed.")
  override def asyncDeleteMessages(messageIds: immutable.Seq[PersistentId], permanent: Boolean): Future[Unit] = {
    log.debug(s"Delete messages, $messageIds")
    sessionProvider.localTx { implicit session =>
      messageIds.foldLeft(Future.successful(())) { (result, id) =>
        val persistenceId = id.persistenceId
        val sequenceNr = id.sequenceNr
        result.flatMap { _ =>
          val sql = if (permanent) {
            sql"DELETE FROM $table WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr"
          } else {
            sql"UPDATE $table SET marker = $DeletedMarker WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr"
          }
          log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId and sequence_nr = $sequenceNr")
          sql.update().future().map(_ => ())
        }
      }
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Future[Unit] = {
    log.debug(s"Delete messages, persistenceId = $persistenceId, toSequenceNr = $toSequenceNr")
    sessionProvider.localTx { implicit session =>
      val sql = if (permanent) {
        sql"DELETE FROM $table WHERE persistence_id = $persistenceId AND sequence_nr <= $toSequenceNr"
      } else {
        sql"UPDATE $table SET marker = $DeletedMarker WHERE persistence_id = $persistenceId AND sequence_nr <= $toSequenceNr AND marker != $DeletedMarker"
      }
      log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId and sequence_nr = $toSequenceNr")
      sql.update().future().map(_ => ())
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug(s"Replay messages, persistenceId = $persistenceId, fromSequenceNr = $fromSequenceNr, toSequenceNr = $toSequenceNr")
    sessionProvider.withPool { implicit session =>
      val sql = sql"SELECT marker, message FROM $table WHERE persistence_id = $persistenceId AND sequence_nr >= $fromSequenceNr AND sequence_nr <= $toSequenceNr ORDER BY sequence_nr ASC LIMIT $max"
      log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId, from_sequence_nr = $fromSequenceNr, to_sequence_nr = $toSequenceNr, limit = $max")
      sql.map(r => (r.string("marker"), r.bytes("message"))).list().future().map { results =>
        results.foreach {
          case (marker, bytes) =>
            val raw = persistenceFromBytes(bytes)
            // It is possible that marker is incompatible with message, for batch updates.
            val message = if (marker == DeletedMarker) raw.update(deleted = true) else raw
            replayCallback(message)
        }
      }
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug(s"Read the highest sequence number, persistenceId = $persistenceId, fromSequenceNr = $fromSequenceNr")
    sessionProvider.withPool { implicit session =>
      val sql = sql"SELECT MAX(sequence_nr) FROM $table WHERE persistence_id = $persistenceId"
      log.debug(s"Execute ${sql.statement} binding persistence_id = $persistenceId and sequence_nr = $fromSequenceNr")
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

object ScalikeJDBCWriteJournal {
  private val AcceptedMarker = "A"
  private val DeletedMarker = "D"
}
