package akka.persistence.snapshot.sqlasync

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.common.{ScalikeJDBCExtension, ScalikeJDBCSessionProvider}
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.serialization.{Serialization, SerializationExtension}
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async._
import scalikejdbc.interpolation.SQLSyntax

private[persistence] trait ScalikeJDBCSnapshotStore extends SnapshotStore with ActorLogging {

  import context.dispatcher

  private[this] val serialization: Serialization = SerializationExtension(context.system)
  private[this] lazy val extension: ScalikeJDBCExtension = ScalikeJDBCExtension(context.system)
  private[this] lazy val sessionProvider: ScalikeJDBCSessionProvider = extension.sessionProvider
  private[this] lazy val table = {
    val tableName = extension.config.snapshotTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug(s"Load a snapshot, persistenceId = $persistenceId, criteria = $criteria")
    sessionProvider.withPool { implicit session =>
      val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, minSequenceNr, minTimestamp) = criteria
      val sql = sql"SELECT * FROM $table WHERE persistence_id = $persistenceId AND sequence_nr <= $maxSequenceNr AND sequence_nr >= $minSequenceNr AND created_at <= $maxTimestamp AND created_at >= $minTimestamp ORDER BY sequence_nr DESC LIMIT 1"
      log.debug(s"Execute ${sql.statement} binding persistence_id = $persistenceId, $minSequenceNr <= sequence_nr <= $maxSequenceNr and $minTimestamp <= created_at <= $maxTimestamp")
      sql.map { result =>
        val Snapshot(snapshot) = serialization.deserialize(result.bytes("snapshot"), classOf[Snapshot]).get
        SelectedSnapshot(
          SnapshotMetadata(result.string("persistence_id"), result.long("sequence_nr"), result.long("created_at")),
          snapshot
        )
      }.single().future()
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    def upsert(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: Array[Byte]): Future[Unit] = {
      def insert(): Future[Unit] = {
        sessionProvider.localTx { implicit session =>
          val sql = sql"INSERT INTO $table (persistence_id, sequence_nr, created_at, snapshot) VALUES ($persistenceId, $sequenceNr, $timestamp, $snapshot)"
          log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId, sequence_nr = $sequenceNr, created_at = $timestamp and snapshot = $snapshot")
          sql.update().future().map(_ => ())
        }
      }
      def update(): Future[Unit] = {
        sessionProvider.localTx { implicit session =>
          val sql = sql"UPDATE $table SET created_at = $timestamp, snapshot = $snapshot WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr"
          log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId, sequence_nr = $sequenceNr, created_at = $timestamp and snapshot = $snapshot")
          sql.update().future().map(_ => ())
        }
      }
      insert().recoverWith { case _ => update() }
    }

    log.debug(s"Save the snapshot, metadata = $metadata, snapshot = $snapshot")
    val SnapshotMetadata(persistenceId, sequenceNr, timestamp) = metadata
    val bytes = serialization.serialize(Snapshot(snapshot)).get
    upsert(persistenceId, sequenceNr, timestamp, bytes)
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    log.debug(s"Delete the snapshot, $metadata")
    sessionProvider.localTx { implicit session =>
      // Ignores the timestamp since the target is specified by the sequence_nr.
      val SnapshotMetadata(persistenceId, sequenceNr, _) = metadata
      val sql = sql"DELETE FROM $table WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr"
      log.debug(s"Execute ${sql.statement}, binding $metadata")
      sql.update().future().map(_ => ())
    }
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    log.debug(s"Delete the snapshot for $persistenceId, criteria = $criteria")
    sessionProvider.localTx { implicit session =>
      val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, minSequenceNr, minTimestamp) = criteria
      val sql = sql"DELETE FROM $table WHERE persistence_id = $persistenceId AND sequence_nr <= $maxSequenceNr AND sequence_nr >= $minSequenceNr AND created_at <= $maxTimestamp AND created_at >= $minTimestamp"
      log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId, $minSequenceNr <= sequence_nr <= $maxSequenceNr and $minTimestamp <= created_at <= $maxTimestamp")
      sql.update().future().map(_ => ())
    }
  }
}
