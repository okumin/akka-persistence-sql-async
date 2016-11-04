package akka.persistence.journal.sqlasync

import akka.persistence.CapabilityFlag
import akka.persistence.helper.PostgreSQLInitializer
import akka.persistence.journal.JournalPerfSpec
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

class PostgreSQLAsyncJournalPerfSpec
  extends JournalPerfSpec(ConfigFactory.load("postgresql-application.conf"))
  with PostgreSQLInitializer {
  override def awaitDurationMillis: Long = 100.seconds.toMillis
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true
}
