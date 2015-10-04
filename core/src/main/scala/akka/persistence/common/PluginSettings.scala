package akka.persistence.common

import akka.actor.{Actor, ActorLogging}
import akka.serialization.{Serialization, SerializationExtension}
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scalikejdbc._
import scalikejdbc.async._

private[persistence] trait PluginSettings extends Actor with ActorLogging {
  implicit protected[this] def persistenceExecutor: ExecutionContext = context.dispatcher
  protected[this] val serialization: Serialization = SerializationExtension(context.system)
  protected[this] lazy val extension: ScalikeJDBCExtension = ScalikeJDBCExtension(context.system)
  protected[this] lazy val sessionProvider: ScalikeJDBCSessionProvider = extension.sessionProvider

  protected[this] lazy val persistenceIdTable = {
    val tableName = extension.config.metadataTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  // PersistenceId => its surrogate key
  private[this] val persistenceIds: TrieMap[String, Long] = TrieMap.empty
  protected[this] def surrogateKeyOf(persistenceId: String)(implicit session: TxAsyncDBSession): Future[Long] = {
    persistenceIds.get(persistenceId) match {
      case Some(id) => Future.successful(id)
      case None =>
        val select = sql"SELECT id FROM $persistenceIdTable WHERE persistence_id = $persistenceId"
        select.map(_.long("id")).single().future().flatMap {
          case Some(id) =>
            persistenceIds.update(persistenceId, id)
            Future.successful(id)
          case None =>
            val insert = sql"INSERT INTO $persistenceIdTable (persistence_id, sequence_nr) VALUES ($persistenceId, 0)"
            for {
              _ <- insert.update().future()
              id <- lastInsertId()
            } yield id
        }
    }
  }

  protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long]

  protected[this] def logging(sql: SQL[Nothing, NoExtractor]): SQL[Nothing, NoExtractor] = {
    log.debug(s"Execute {} binding {}", sql.statement, sql.parameters)
    sql
  }
}

private[persistence] trait MySQLPluginSettings extends PluginSettings {
  override protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long] = {
    val sql = sql"SELECT LAST_INSERT_ID() AS id;"
    sql.map(_.long("id")).single().future().map(_.get)
  }
}

private[persistence] trait PostgreSQLPluginSettings extends PluginSettings {
  override protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long] = {
    val sql = sql"SELECT LASTVAL() AS id;"
    sql.map(_.long("id")).single().future().map(_.get)
  }
}
