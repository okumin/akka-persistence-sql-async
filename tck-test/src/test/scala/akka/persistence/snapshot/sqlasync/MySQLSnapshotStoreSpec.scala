package akka.persistence.snapshot.sqlasync

import akka.persistence.helper.MySQLInitializer
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

class MySQLSnapshotStoreSpec
  extends SnapshotStoreSpec(ConfigFactory.load("mysql-application.conf"))
  with MySQLInitializer
