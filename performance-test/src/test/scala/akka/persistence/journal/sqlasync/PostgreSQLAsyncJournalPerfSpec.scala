package akka.persistence.journal.sqlasync

import akka.persistence.helper.PostgreSQLInitializer
import akka.persistence.journal.{JournalPerfSpec, JournalSpec}
import scala.concurrent.duration._

class PostgreSQLAsyncJournalPerfSpec extends JournalPerfSpec with JournalSpec with PostgreSQLInitializer {
  override def awaitDurationMillis: Long = 100.seconds.toMillis
}
