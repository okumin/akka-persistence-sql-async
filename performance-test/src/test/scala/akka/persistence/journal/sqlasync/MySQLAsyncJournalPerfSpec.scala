package akka.persistence.journal.sqlasync

import akka.persistence.helper.MySQLInitializer
import akka.persistence.journal.JournalPerfSpec
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

class MySQLAsyncJournalPerfSpec
  extends JournalPerfSpec(ConfigFactory.load("mysql-application.conf"))
  with MySQLInitializer {
  override def awaitDurationMillis: Long = 100.seconds.toMillis
}
