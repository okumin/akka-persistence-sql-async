/*
 * Copyright 2014 okumin.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.snapshot.sqlasync

import akka.persistence._
import akka.persistence.common.StoragePlugin
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async._
import scalikejdbc.interpolation.SQLSyntax

private[persistence] trait ScalikeJDBCSnapshotStore extends SnapshotStore with StoragePlugin {
  protected[this] lazy val snapshotTable = {
    val tableName = extension.config.snapshotTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  override def loadAsync(persistenceId: String,
                         criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug("Load a snapshot, persistenceId = {}, criteria = {}", persistenceId, criteria)
    sessionProvider.localTx { implicit session =>
      val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, minSequenceNr, minTimestamp) =
        criteria
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"SELECT * FROM $snapshotTable WHERE persistence_key = $key AND sequence_nr >= $minSequenceNr AND sequence_nr <= $maxSequenceNr AND created_at >= $minTimestamp AND created_at <= $maxTimestamp ORDER BY sequence_nr DESC LIMIT 1"
        snapshot <- logging(sql)
          .map { result =>
            val Snapshot(snapshot) =
              serialization.deserialize(result.bytes("snapshot"), classOf[Snapshot]).get
            SelectedSnapshot(
              SnapshotMetadata(persistenceId,
                               result.long("sequence_nr"),
                               result.long("created_at")),
              snapshot
            )
          }
          .single()
          .future()
      } yield snapshot
    }
  }

  protected[this] def upsert(persistenceId: String,
                             sequenceNr: Long,
                             timestamp: Long,
                             snapshot: Array[Byte]): Future[Unit]

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
        sql = sql"DELETE FROM $snapshotTable WHERE persistence_key = $key AND sequence_nr = $sequenceNr"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }

  override def deleteAsync(persistenceId: String,
                           criteria: SnapshotSelectionCriteria): Future[Unit] = {
    log.debug("Delete the snapshot for {}, criteria = {}", persistenceId, criteria)
    val SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, minSequenceNr, minTimestamp) =
      criteria
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"DELETE FROM $snapshotTable WHERE persistence_key = $key AND sequence_nr <= $maxSequenceNr AND sequence_nr >= $minSequenceNr AND created_at <= $maxTimestamp AND created_at >= $minTimestamp"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }
}
