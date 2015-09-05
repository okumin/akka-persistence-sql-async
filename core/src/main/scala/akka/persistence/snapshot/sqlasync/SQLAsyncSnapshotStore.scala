package akka.persistence.snapshot.sqlasync

import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async._

class MySQLSnapshotStore extends ScalikeJDBCSnapshotStore {

  import context.dispatcher

  override protected[this] def upsert(persistenceId: String,
                                      sequenceNr: Long,
                                      timestamp: Long,
                                      snapshot: Array[Byte]): Future[Unit] = {
    sessionProvider.localTx { implicit session =>
      val sql = sql"INSERT INTO $table (persistence_id, sequence_nr, created_at, snapshot) VALUES ($persistenceId, $sequenceNr, $timestamp, $snapshot) ON DUPLICATE KEY UPDATE created_at = $timestamp, snapshot = $snapshot"
      log.debug("Execute {}, binding persistence_id = {}, sequence_nr = {}, created_at = {}", sql.statement, persistenceId, sequenceNr, timestamp)
      sql.update().future().map(_ => ())
    }
  }
}

class PostgreSQLSnapshotStore extends ScalikeJDBCSnapshotStore
