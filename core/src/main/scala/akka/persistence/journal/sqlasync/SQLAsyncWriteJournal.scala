package akka.persistence.journal.sqlasync

import akka.persistence.common.{MySQLPluginSettings, PostgreSQLPluginSettings}
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async.{TxAsyncDBSession, _}

class MySQLAsyncWriteJournal extends ScalikeJDBCWriteJournal with MySQLPluginSettings {
  override protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)
                                               (implicit session: TxAsyncDBSession): Future[Unit] = {
    val sql = sql"INSERT INTO $metadataTable (persistence_id, sequence_nr) VALUES ($persistenceId, $sequenceNr) ON DUPLICATE KEY UPDATE sequence_nr = $sequenceNr"
    logging(sql).update().future().map(_ => ())
  }
}

class PostgreSQLAsyncWriteJournal extends ScalikeJDBCWriteJournal with PostgreSQLPluginSettings {
  override protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)
                                               (implicit session: TxAsyncDBSession): Future[Unit] = {
    val sql = sql"WITH upsert AS (UPDATE $metadataTable SET sequence_nr = $sequenceNr WHERE persistence_id = $persistenceId RETURNING *) INSERT INTO $metadataTable (persistence_id, sequence_nr) SELECT $persistenceId, $sequenceNr WHERE NOT EXISTS (SELECT * FROM upsert)"
    logging(sql).update().future().map(_ => ())
  }
}
