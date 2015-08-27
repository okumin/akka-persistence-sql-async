package akka.persistence.journal.sqlasync

import akka.persistence.helper.MySQLInitializer
import akka.persistence.journal.{JournalPerfSpec, JournalSpec}
import scala.concurrent.duration._

class MySQLAsyncJournalPerfSpec extends JournalPerfSpec with JournalSpec with MySQLInitializer {
  override def awaitDurationMillis: Long = 100.seconds.toMillis
}
