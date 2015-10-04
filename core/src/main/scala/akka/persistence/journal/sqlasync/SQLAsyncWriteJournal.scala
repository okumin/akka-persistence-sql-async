package akka.persistence.journal.sqlasync

import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async.{TxAsyncDBSession, _}

class MySQLAsyncWriteJournal extends ScalikeJDBCWriteJournal {
  import context.dispatcher

  override protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)
                                               (implicit session: TxAsyncDBSession): Future[Unit] = {
    log.debug("Update the highest sequence_nr of {} into {}.", persistenceId, sequenceNr)
    val sql = sql"INSERT INTO $persistenceIdTable (persistence_id, sequence_nr) VALUES ($persistenceId, $sequenceNr) ON DUPLICATE KEY UPDATE sequence_nr = $sequenceNr"
    log.debug("Execute {}, binding persistence_id = {}, sequence_nr = {}", sql, persistenceId, sequenceNr)
    sql.update().future().map(_ => ())
  }

  override protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long] = {
    val sql = sql"SELECT LAST_INSERT_ID() AS id;"
    sql.map(_.long("id")).single().future().map(_.get)
  }
}

class PostgreSQLAsyncWriteJournal extends ScalikeJDBCWriteJournal {
  import context.dispatcher

  override protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)
                                               (implicit session: TxAsyncDBSession): Future[Unit] = {
    log.debug("Update the highest sequence_nr of {} into {}.", persistenceId, sequenceNr)
    val sql = sql"WITH upsert AS (UPDATE $persistenceIdTable SET sequence_nr = $sequenceNr WHERE persistence_id = $persistenceId RETURNING *) INSERT INTO $persistenceIdTable (persistence_id, sequence_nr) SELECT $persistenceId, $sequenceNr WHERE NOT EXISTS (SELECT * FROM upsert)"
    log.debug("Execute {}, binding persistence_id = {}, sequence_nr = {}", sql, persistenceId, sequenceNr)
    sql.update().future().map(_ => ())
  }

  override protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long] = {
    val sql = sql"SELECT LASTVAL() AS id;"
    sql.map(_.long("id")).single().future().map(_.get)
  }
}
