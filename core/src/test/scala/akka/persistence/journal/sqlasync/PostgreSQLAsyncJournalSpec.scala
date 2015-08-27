package akka.persistence.journal.sqlasync

import akka.persistence.helper.PostgreSQLInitializer
import akka.persistence.journal.JournalSpec

class PostgreSQLAsyncJournalSpec extends JournalSpec with PostgreSQLInitializer
