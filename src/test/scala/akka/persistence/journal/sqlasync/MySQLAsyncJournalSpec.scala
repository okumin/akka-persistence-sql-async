package akka.persistence.journal.sqlasync

import akka.persistence.helper.MySQLInitializer
import akka.persistence.journal.JournalSpec

class MySQLAsyncJournalSpec extends JournalSpec with MySQLInitializer
