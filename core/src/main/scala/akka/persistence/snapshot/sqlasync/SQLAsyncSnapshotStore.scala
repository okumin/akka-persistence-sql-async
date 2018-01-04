/*
 * Copyright 2014 okumin.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.snapshot.sqlasync

import akka.persistence.common.{MySQLPlugin, PostgreSQLPlugin}
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async._

class MySQLSnapshotStore extends ScalikeJDBCSnapshotStore with MySQLPlugin {
  override protected[this] def upsert(persistenceId: String,
                                      sequenceNr: Long,
                                      timestamp: Long,
                                      snapshot: Array[Byte]): Future[Unit] = {
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"INSERT INTO $snapshotTable (persistence_key, sequence_nr, created_at, snapshot) VALUES ($key, $sequenceNr, $timestamp, $snapshot) ON DUPLICATE KEY UPDATE created_at = $timestamp, snapshot = $snapshot"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }
}

class PostgreSQLSnapshotStore extends ScalikeJDBCSnapshotStore with PostgreSQLPlugin {
  override protected[this] def upsert(persistenceId: String,
                                      sequenceNr: Long,
                                      timestamp: Long,
                                      snapshot: Array[Byte]): Future[Unit] = {
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"WITH upsert AS (UPDATE $snapshotTable SET created_at = $timestamp, snapshot = $snapshot WHERE persistence_key = $key AND sequence_nr = $sequenceNr RETURNING *) INSERT INTO $snapshotTable (persistence_key, sequence_nr, created_at, snapshot) SELECT $key, $sequenceNr, $timestamp, $snapshot WHERE NOT EXISTS (SELECT * FROM upsert)"
        _ <- logging(sql).update().future()
      } yield ()
    }
  }
}
