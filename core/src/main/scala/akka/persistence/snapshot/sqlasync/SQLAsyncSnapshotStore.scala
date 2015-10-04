package akka.persistence.snapshot.sqlasync

import akka.persistence.common.{MySQLPluginSettings, PostgreSQLPluginSettings}
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async._

class MySQLSnapshotStore extends ScalikeJDBCSnapshotStore with MySQLPluginSettings {
  override protected[this] def upsert(persistenceId: String,
                                      sequenceNr: Long,
                                      timestamp: Long,
                                      snapshot: Array[Byte]): Future[Unit] = {
    sessionProvider.localTx { implicit session =>
      val sql = sql"INSERT INTO $snapshotTable (persistence_id, sequence_nr, created_at, snapshot) VALUES ($persistenceId, $sequenceNr, $timestamp, $snapshot) ON DUPLICATE KEY UPDATE created_at = $timestamp, snapshot = $snapshot"
      logging(sql).update().future().map(_ => ())
    }
  }
}

class PostgreSQLSnapshotStore extends ScalikeJDBCSnapshotStore with PostgreSQLPluginSettings {
  override protected[this] def upsert(persistenceId: String,
                                      sequenceNr: Long,
                                      timestamp: Long,
                                      snapshot: Array[Byte]): Future[Unit] = {
    sessionProvider.localTx { implicit session =>
      val sql = sql"WITH upsert AS (UPDATE $snapshotTable SET created_at = $timestamp, snapshot = $snapshot WHERE persistence_id = $persistenceId AND sequence_nr = $sequenceNr RETURNING *) INSERT INTO $snapshotTable (persistence_id, sequence_nr, created_at, snapshot) SELECT $persistenceId, $sequenceNr, $timestamp, $snapshot WHERE NOT EXISTS (SELECT * FROM upsert)"
      log.debug("Execute {}, binding persistence_id = {}, sequence_nr = {}, created_at = {}", sql.statement, persistenceId, sequenceNr, timestamp)
      logging(sql).update().future().map(_ => ())
    }
  }
}
