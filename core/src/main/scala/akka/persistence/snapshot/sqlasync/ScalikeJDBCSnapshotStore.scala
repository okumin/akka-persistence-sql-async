package akka.persistence.snapshot.sqlasync

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.common.{ScalikeJDBCExtension, ScalikeJDBCSessionProvider}
import akka.persistence.serialization.Snapshot
import akka.serialization.{Serialization, SerializationExtension}
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async._
import scalikejdbc.interpolation.SQLSyntax

private[persistence] trait ScalikeJDBCSnapshotStore extends AsyncSnapshotStore with ActorLogging {

  import context.dispatcher

  private[this] val serialization: Serialization = SerializationExtension(context.system)
  private[this] lazy val extension: ScalikeJDBCExtension = ScalikeJDBCExtension(context.system)
  private[this] lazy val sessionProvider: ScalikeJDBCSessionProvider = extension.sessionProvider
  private[this] lazy val table = {
    val tableName = extension.config.snapshotTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  override protected lazy val publish = {
    Persistence(context.system).settings.internal.publishPluginCommands
  }

  override protected def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug(s"Load a snapshot, persistenceId = $persistenceId, criteria = $criteria")
    sessionProvider.withPool { implicit session =>
      val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp) = criteria
      val sql = sql"SELECT * FROM $table WHERE persistence_id = $persistenceId AND sequence_nr <= $maxSequenceNr AND created_at <= $maxTimestamp ORDER BY sequence_nr DESC LIMIT 1"
      log.debug(s"Execute ${sql.statement} binding persistence_id = $persistenceId, sequence_nr = $maxSequenceNr and created_at = $maxTimestamp")
      sql.map { result =>
        val Snapshot(snapshot) = serialization.deserialize(result.bytes("snapshot"), classOf[Snapshot]).get
        SelectedSnapshot(
          SnapshotMetadata(result.string("persistence_id"), result.long("sequence_nr"), result.long("created_at")),
          snapshot
        )
      }.single().future()
    }
  }

  override protected def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    log.debug(s"Save the snapshot, metadata = $metadata, snapshot = $snapshot")
    sessionProvider.localTx { implicit session =>
      val SnapshotMetadata(persistenceId, sequenceNr, createdAt) = metadata
      val bytes = serialization.serialize(Snapshot(snapshot)).get
      val sql = sql"INSERT INTO $table (persistence_id, sequence_nr, created_at, snapshot) VALUES ($persistenceId, $sequenceNr, $createdAt, $bytes)"
      log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId, sequence_nr = $sequenceNr, created_at = $createdAt and snapshot = $snapshot")
      sql.update().future().map(_ => ())
    }
  }

  override protected def saved(metadata: SnapshotMetadata): Unit = {
    log.debug(s"Saved $metadata")
  }

  override protected def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    log.debug(s"Delete the snapshot, $metadata")
    sessionProvider.localTx { implicit session =>
      val SnapshotMetadata(persistenceId, sequenceNr, _) = metadata
      val sql = sql"DELETE FROM $table WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr"
      log.debug(s"Execute ${sql.statement}, binding $metadata")
      sql.update().future().map(_ => ())
    }
  }

  override protected def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    log.debug(s"Delete the snapshot for $persistenceId, criteria = $criteria")
    sessionProvider.localTx { implicit session =>
      val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp) = criteria
      val sql = sql"DELETE FROM $table WHERE persistence_id = $persistenceId AND sequence_nr <= $maxSequenceNr AND created_at <= $maxTimestamp"
      log.debug(s"Execute ${sql.statement}, binding persistence_id = $persistenceId, sequence_nr = $maxSequenceNr and created_at = $maxTimestamp")
      sql.update().future().map(_ => ())
    }
  }
}
