# akka-persistence-sql-async

A journal and snapshot store plugin for [akka-persistence](http://doc.akka.io/docs/akka/2.3.6/scala/persistence.html) using RDBMS.
Akka-persistence-sql-async executes queries by [ScalikeJDBC-Async](https://github.com/scalikejdbc/scalikejdbc-async) that provides non-blocking APIs to talk to databases.


Akka-persistence-sql-async supports following databases.
- MySQL
- PostgreSQL

## Usage

In application.conf,

```
akka {
  persistence {
    journal.plugin = "akka-persistence-sql-async.journal"
    snapshot-store.plugin = "akka-persistence-sql-async.snapshot-store"

    # disable leveldb (default store impl)
    journal.leveldb.native = off
  }
}

akka-persistence-sql-async {
  journal.class = "akka.persistence.journal.sqlasync.MySQLAsyncWriteJournal"
  snapshot-store.class = "akka.persistence.snapshot.sqlasync.MySQLSnapshotStore"
  
  # For PostgreSQL
  # journal.class = "akka.persistence.journal.sqlasync.PostgreSQLAsyncWriteJournal"
  # snapshot-store.class = "akka.persistence.snapshot.sqlasync.PostgreSQLSnapshotStore"

  user = "root"
  pass = ""
  url = "jdbc:mysql://localhost/akka_persistence_sql_async"
  max-pool-size = 4
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

## License

Apache 2.0
