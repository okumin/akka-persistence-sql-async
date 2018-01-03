package akka.persistence.common

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem

import scala.concurrent.duration._

private[persistence] class SQLAsyncConfig(val system: ActorSystem) {
  val rootKey = "akka-persistence-sql-async"
  val config = system.settings.config.getConfig(rootKey)

  val user = config.getString("user")
  val password = config.getString("password")
  val url = config.getString("url")
  val maxPoolSize = config.getInt("max-pool-size")
  val waitQueueCapacity = config.getInt("wait-queue-capacity")

  val metadataTableName = config.getString("metadata-table-name")
  val journalTableName = config.getString("journal-table-name")
  val snapshotTableName = config.getString("snapshot-table-name")

  val connectTimeout: Option[Duration] = getOptionalDuration("connect-timeout", 5.seconds)
  val queryTimeout: Option[Duration] = getOptionalDuration("query-timeout", 5.seconds)

  private def getOptionalDuration(configKey: String, defaultValue: FiniteDuration) = {
    if (config.hasPath(configKey)) {
      val duration = config.getDuration(configKey, TimeUnit.NANOSECONDS)
      Option(FiniteDuration(duration, TimeUnit.NANOSECONDS))
    } else {
      Option(defaultValue)
    }
  }
}

private[persistence] object SQLAsyncConfig {
  def apply(system: ActorSystem): SQLAsyncConfig = new SQLAsyncConfig(system)
}
