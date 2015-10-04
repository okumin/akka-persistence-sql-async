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
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"INSERT INTO $snapshotTable (persistence_key, sequence_nr, created_at, snapshot) VALUES ($key, $sequenceNr, $timestamp, $snapshot) ON DUPLICATE KEY UPDATE created_at = $timestamp, snapshot = $snapshot"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }
}

class PostgreSQLSnapshotStore extends ScalikeJDBCSnapshotStore with PostgreSQLPluginSettings {
  override protected[this] def upsert(persistenceId: String,
                                      sequenceNr: Long,
                                      timestamp: Long,
                                      snapshot: Array[Byte]): Future[Unit] = {
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"WITH upsert AS (UPDATE $snapshotTable SET created_at = $timestamp, snapshot = $snapshot WHERE persistence_key = $key AND sequence_nr = $sequenceNr RETURNING *) INSERT INTO $snapshotTable (persistence_key, sequence_nr, created_at, snapshot) SELECT $key, $sequenceNr, $timestamp, $snapshot WHERE NOT EXISTS (SELECT * FROM upsert)"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }
}
