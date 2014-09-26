package akka.persistence.journal.sqlasync

import akka.persistence.helper.MySQLInitializer
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ConfigFactory, Config}

class MySQLAsyncJournalSpec extends JournalSpec with MySQLInitializer {
  override lazy val config: Config = ConfigFactory.parseString(
    """
      |akka.persistence.journal.plugin = "akka-persistence-sql-async.journal"
      |
      |akka-persistence-sql-async {
      |  journal.class = "akka.persistence.journal.sqlasync.MySQLAsyncWriteJournal"
      |
      |  user = "root"
      |  pass = ""
      |  url = "jdbc:mysql://localhost/akka_persistence_sql_async"
      |  max-pool-size = 4
      |  journal-table-name = "journal"
      |  snapshot-table-name = "snapshot"
      |}
    """.stripMargin)
}
