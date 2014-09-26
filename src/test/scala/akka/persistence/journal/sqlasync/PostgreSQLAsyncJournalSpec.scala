package akka.persistence.journal.sqlasync

import akka.persistence.helper.PostgreSQLInitializer
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ConfigFactory, Config}

class PostgreSQLAsyncJournalSpec extends JournalSpec with PostgreSQLInitializer {
  override lazy val config: Config = ConfigFactory.parseString(
    """
      |akka.persistence.journal.plugin = "akka-persistence-sql-async.journal"
      |
      |akka-persistence-sql-async {
      |  journal.class = "akka.persistence.journal.sqlasync.PostgreSQLAsyncWriteJournal"
      |
      |  user = "root"
      |  pass = ""
      |  url = "jdbc:postgresql://localhost/akka_persistence_sql_async"
      |  max-pool-size = 4
      |  journal-table-name = "journal"
      |  snapshot-table-name = "snapshot"
      |}
    """.stripMargin)
}
