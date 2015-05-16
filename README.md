# akka-persistence-sql-async

[![Build Status](https://travis-ci.org/okumin/akka-persistence-sql-async.svg?branch=master)](https://travis-ci.org/okumin/akka-persistence-sql-async)

A journal and snapshot store plugin for [akka-persistence](http://doc.akka.io/docs/akka/2.3.11/scala/persistence.html) using RDBMS.
Akka-persistence-sql-async executes queries by [ScalikeJDBC-Async](https://github.com/scalikejdbc/scalikejdbc-async) that provides non-blocking APIs to talk to databases.


Akka-persistence-sql-async supports following databases.
- MySQL
- PostgreSQL

This library is tested against [akka-persistence-tck](http://doc.akka.io/docs/akka/2.3.11/scala/persistence.html#plugin-tck).

## Usage

### Dependency

You should add the following dependency.

```
libraryDependencies += "com.okumin" %% "akka-persistence-sql-async" % "0.2"
```

And then, please include the mysql-async if you use MySQL.

```
libraryDependencies += "com.github.mauricio" %% "mysql-async" % "0.2.16"
```

And if you use PostgreSQL.

```
libraryDependencies += "com.github.mauricio" %% "postgresql-async" % "0.2.16"
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
  max-pool-size = 4 # total connection count
  wait-queue-capacity = 10000 # If query cannot be executed soon, it wait in the queue and will be executed later.
  journal-table-name = "journal"
  snapshot-table-name = "snapshot"
}
```

## Table schema

Create the database and tables for journal and snapshot store.

### MySQL

```
CREATE TABLE IF NOT EXISTS {your_journal_table_name} (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  marker VARCHAR(255) NOT NULL,
  message BLOB NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);

CREATE TABLE IF NOT EXISTS {your_snapshot_table_name} (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  snapshot BLOB NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);
```

### PostgreSQL

```
CREATE TABLE IF NOT EXISTS {your_journal_table_name} (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  marker VARCHAR(255) NOT NULL,
  message BYTEA NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);


CREATE TABLE IF NOT EXISTS {your_snapshot_table_name} (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);
```

## Release Notes

### 0.2 - Apr 5, 2015
- [Change pass to password in configuration](https://github.com/okumin/akka-persistence-sql-async/issues/3)

### 0.1 - Sep 30, 2014
- The first release

## License

Apache 2.0
