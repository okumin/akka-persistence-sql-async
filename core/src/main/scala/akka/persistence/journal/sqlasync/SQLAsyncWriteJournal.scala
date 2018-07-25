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

package akka.persistence.journal.sqlasync

import akka.persistence.common.{MySQLPlugin, PostgreSQLPlugin}
import scala.concurrent.Future
import scalikejdbc._
import scalikejdbc.async.{TxAsyncDBSession, _}

class MySQLAsyncWriteJournal extends ScalikeJDBCWriteJournal with MySQLPlugin {
  override protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)(
      implicit session: TxAsyncDBSession): Future[Unit] = {
    val sql =
      sql"INSERT INTO $metadataTable (persistence_id, sequence_nr) VALUES ($persistenceId, $sequenceNr) ON DUPLICATE KEY UPDATE sequence_nr = $sequenceNr"
    logging(sql).update().future().map(_ => ())
  }
}

class PostgreSQLAsyncWriteJournal extends ScalikeJDBCWriteJournal with PostgreSQLPlugin {
  override protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)(
      implicit session: TxAsyncDBSession): Future[Unit] = {
    val sql =
      sql"INSERT INTO $metadataTable (persistence_id, sequence_nr) VALUES ($persistenceId, $sequenceNr) ON CONFLICT (persistence_id) DO UPDATE SET sequence_nr = $sequenceNr"
    logging(sql).update().future().map(_ => ())
  }
}
