package akka.persistence.snapshot.sqlasync

import akka.persistence._
import akka.persistence.common.PluginSettings
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async._
import scalikejdbc.interpolation.SQLSyntax

private[persistence] trait ScalikeJDBCSnapshotStore extends SnapshotStore with PluginSettings {
  protected[this] lazy val snapshotTable = {
    val tableName = extension.config.snapshotTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug("Load a snapshot, persistenceId = {}, criteria = {}", persistenceId, criteria)
    sessionProvider.localTx { implicit session =>
      val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, minSequenceNr, minTimestamp) = criteria
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"SELECT * FROM $snapshotTable WHERE persistence_id = $key AND sequence_nr <= $maxSequenceNr AND sequence_nr >= $minSequenceNr AND created_at <= $maxTimestamp AND created_at >= $minTimestamp ORDER BY sequence_nr DESC LIMIT 1"
        snapshot <- logging(sql).map { result =>
          val Snapshot(snapshot) = serialization.deserialize(result.bytes("snapshot"), classOf[Snapshot]).get
          SelectedSnapshot(
            SnapshotMetadata(persistenceId, result.long("sequence_nr"), result.long("created_at")),
            snapshot
          )
        }.single().future()
      } yield snapshot
    }
  }

  protected[this] def upsert(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: Array[Byte]): Future[Unit]

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    log.debug("Save the snapshot, metadata = {}, snapshot = {}", metadata, snapshot)
    val SnapshotMetadata(persistenceId, sequenceNr, timestamp) = metadata
    val bytes = serialization.serialize(Snapshot(snapshot)).get
    upsert(persistenceId, sequenceNr, timestamp, bytes)
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    log.debug("Delete the snapshot, {}", metadata)
    val SnapshotMetadata(persistenceId, sequenceNr, _) = metadata
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        // Ignores the timestamp since the target is specified by the sequence_nr.
        sql = sql"DELETE FROM $snapshotTable WHERE persistence_id = $key AND sequence_nr = $sequenceNr"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    log.debug("Delete the snapshot for {}, criteria = {}", persistenceId, criteria)
    val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, minSequenceNr, minTimestamp) = criteria
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"DELETE FROM $snapshotTable WHERE persistence_id = $key AND sequence_nr <= $maxSequenceNr AND sequence_nr >= $minSequenceNr AND created_at <= $maxTimestamp AND created_at >= $minTimestamp"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }
}
