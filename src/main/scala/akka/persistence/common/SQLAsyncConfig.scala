package akka.persistence.common

import akka.actor.ActorSystem

private[persistence] class SQLAsyncConfig(val system: ActorSystem) {
  val rootKey = "akka-persistence-sql-async"
  val config = system.settings.config.getConfig(rootKey)

  val user = config.getString("user")
  val pass = config.getString("pass")
  val url = config.getString("url")
  val maxPoolSize = config.getInt("max-pool-size")
  val journalTableName = config.getString("journal-table-name")
  val snapshotTableName = config.getString("snapshot-table-name")
}

private[persistence] object SQLAsyncConfig {
  def apply(system: ActorSystem): SQLAsyncConfig = new SQLAsyncConfig(system)
}
