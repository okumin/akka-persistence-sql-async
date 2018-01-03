package akka.persistence.journal.sqlasync

import akka.persistence.CapabilityFlag
import akka.persistence.helper.PostgreSQLInitializer
import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory

class PostgreSQLAsyncJournalSpec
    extends JournalSpec(ConfigFactory.load("postgresql-application.conf"))
    with PostgreSQLInitializer {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true
}
