# akka-persistence-sql-async

[![Build Status](https://travis-ci.org/andriimartynov/akka-persistence-sql-async.svg)](https://travis-ci.org/andriimartynov/akka-persistence-sql-async)

A journal and snapshot store plugin for [akka-persistence](http://doc.akka.io/docs/akka/2.4.12/scala/persistence.html) using RDBMS.
Akka-persistence-sql-async executes queries by [ScalikeJDBC-Async](https://github.com/scalikejdbc/scalikejdbc-async) that provides non-blocking APIs to talk to databases.


Akka-persistence-sql-async supports following databases.
- MySQL
- PostgreSQL

This library is tested against [akka-persistence-tck](http://doc.akka.io/docs/akka/2.4.12/scala/persistence.html#plugin-tck).

## Usage

### Dependency

You should add the following dependency.

```
libraryDependencies += "com.okumin" %% "akka-persistence-sql-async" % "0.5.1"
```

And then, please include the mysql-async if you use MySQL.

```
libraryDependencies += "com.github.mauricio" %% "mysql-async" % "0.2.20"
```

And if you use PostgreSQL.

```
libraryDependencies += "com.github.mauricio" %% "postgresql-async" % "0.2.20"
```

### Configuration

In `application.conf`,

```
akka {
  persistence {
    journal.plugin = "akka-persistence-sql-async.journal"
    snapshot-store.plugin = "akka-persistence-sql-async.snapshot-store"
  }
}

akka-persistence-sql-async {
  journal.class = "akka.persistence.journal.sqlasync.MySQLAsyncWriteJournal"
  snapshot-store.class = "akka.persistence.snapshot.sqlasync.MySQLSnapshotStore"

  # For PostgreSQL
  # journal.class = "akka.persistence.journal.sqlasync.PostgreSQLAsyncWriteJournal"
  # snapshot-store.class = "akka.persistence.snapshot.sqlasync.PostgreSQLSnapshotStore"

  user = "root"
  password = ""
  url = "jdbc:mysql://localhost/akka_persistence_sql_async"
  max-pool-size = 4
  wait-queue-capacity = 10000

  metadata-table-name = "persistence_metadata"
  journal-table-name = "persistence_journal"
  snapshot-table-name = "persistence_snapshot"
  
  connect-timeout = 5s
  query-timeout = 5s
}
```

## Table schema

Create the database and tables for journal and snapshot store.

### MySQL

```
CREATE TABLE IF NOT EXISTS {your_metadata_table_name} (
  persistence_key BIGINT NOT NULL AUTO_INCREMENT,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  PRIMARY KEY (persistence_key),
  UNIQUE (persistence_id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS {your_journal_table_name} (
  persistence_key BIGINT NOT NULL,
  sequence_nr BIGINT NOT NULL,
  message LONGBLOB NOT NULL,
  PRIMARY KEY (persistence_key, sequence_nr),
  FOREIGN KEY (persistence_key) REFERENCES {your_metadata_table_name} (persistence_key)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS {your_snapshot_table_name} (
  persistence_key BIGINT NOT NULL,
  sequence_nr BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  snapshot LONGBLOB NOT NULL,
  PRIMARY KEY (persistence_key, sequence_nr),
  FOREIGN KEY (persistence_key) REFERENCES {your_metadata_table_name} (persistence_key)
) ENGINE = InnoDB;
```

### PostgreSQL

```
CREATE TABLE IF NOT EXISTS {your_metadata_table_name} (
  persistence_key BIGSERIAL NOT NULL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  PRIMARY KEY (persistence_key),
  UNIQUE (persistence_id)
);

CREATE TABLE IF NOT EXISTS {your_journal_table_name} (
  persistence_key BIGINT NOT NULL REFERENCES {your_metadata_table_name}(persistence_key),
  sequence_nr BIGINT NOT NULL,
  message BYTEA NOT NULL,
  PRIMARY KEY (persistence_key, sequence_nr)
);

CREATE TABLE IF NOT EXISTS {your_snapshot_table_name} (
  persistence_key BIGINT NOT NULL REFERENCES {your_metadata_table_name}(persistence_key),
  sequence_nr BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY (persistence_key, sequence_nr)
);
```

## Release Notes

### 0.5.1 - Jan 2, 2018
- Support connect/query timeout

### 0.5.0 - Nov 8, 2017
- Update dependencies
- Scala 2.12 support

### 0.4.0 - Nov 5, 2016
- Update mysql-async and postgresql-async

### 0.3.1 - Oct 15, 2015
- [Never execute invalid SQLs](https://github.com/okumin/akka-persistence-sql-async/issues/10)

### 0.3.0 - Oct 7, 2015
- Update Akka to 2.4

### 0.2.1 - Sep 25, 2015
- [Keep the highest sequence number](https://github.com/okumin/akka-persistence-sql-async/issues/6)

### 0.2 - Apr 5, 2015
- [Change pass to password in configuration](https://github.com/okumin/akka-persistence-sql-async/issues/3)

### 0.1 - Sep 30, 2014
- The first release

## License

Apache 2.0
