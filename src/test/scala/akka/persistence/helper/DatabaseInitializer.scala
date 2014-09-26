package akka.persistence.helper

import akka.persistence.PluginSpec
import akka.persistence.common.{ScalikeJDBCExtension, SQLAsyncConfig}
import scala.concurrent.Await
import scala.concurrent.duration._
import scalikejdbc.SQL
import scalikejdbc.async._

trait DatabaseInitializer extends PluginSpec {
  protected val sqlAsyncConfig: SQLAsyncConfig = ScalikeJDBCExtension(system).config
  private[this] def executeDDL[A](ddl: String): Unit = {
    implicit val executor = sqlAsyncConfig.system.dispatcher
    val provider = ScalikeJDBCExtension(sqlAsyncConfig.system).sessionProvider
    val result = provider.localTx { implicit session =>
      SQL(ddl).update().future().map(_ => ())
    }
    Await.result(result, 10.seconds)
  }
  protected def createJournalTableDDL: String
  private[this] def dropJournalTableDDL: String = {
    s"DROP TABLE IF EXISTS ${sqlAsyncConfig.journalTableName}"
  }

  /**
   * Flush DB.
   */
  protected override def beforeAll(): Unit = {
    executeDDL(dropJournalTableDDL)
    executeDDL(createJournalTableDDL)
    super.beforeAll()
  }
}

trait MySQLInitializer extends DatabaseInitializer {
  override protected def createJournalTableDDL: String = {
    s"""
        |CREATE TABLE IF NOT EXISTS ${sqlAsyncConfig.journalTableName} (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_nr BIGINT NOT NULL,
        |  marker VARCHAR(255) NOT NULL,
        |  message BLOB NOT NULL,
        |  created_at TIMESTAMP NOT NULL,
        |  PRIMARY KEY (persistence_id, sequence_nr)
        |)
      """.stripMargin
  }
}

trait PostgreSQLInitializer extends DatabaseInitializer {
  override protected def createJournalTableDDL: String = {
    s"""
        |CREATE TABLE IF NOT EXISTS ${sqlAsyncConfig.journalTableName} (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_nr BIGINT NOT NULL,
        |  marker VARCHAR(255) NOT NULL,
        |  message BYTEA NOT NULL,
        |  created_at TIMESTAMP NOT NULL,
        |  PRIMARY KEY (persistence_id, sequence_nr)
        |)
      """.stripMargin
  }
}
