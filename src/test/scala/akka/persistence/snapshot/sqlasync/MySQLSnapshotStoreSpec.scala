package akka.persistence.snapshot.sqlasync

import akka.persistence.helper.MySQLInitializer
import akka.persistence.snapshot.SnapshotStoreSpec

class MySQLSnapshotStoreSpec extends SnapshotStoreSpec with MySQLInitializer
