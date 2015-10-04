package akka.persistence.snapshot.sqlasync

import akka.persistence.helper.PostgreSQLInitializer
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

class PostgreSQLSnapshotStoreSpec
  extends SnapshotStoreSpec(ConfigFactory.load("postgresql-application.conf"))
  with PostgreSQLInitializer
