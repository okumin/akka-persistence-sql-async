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

package akka.persistence.common

import scala.concurrent.{ExecutionContext, Future}
import scalikejdbc.async._

private[persistence] trait ScalikeJDBCSessionProvider {

  /**
    * Provides a shared session.
    */
  def withPool[A](f: SharedAsyncDBSession => Future[A]): Future[A]

  /**
    * Provides a session for transaction.
    */
  def localTx[A](f: TxAsyncDBSession => Future[A]): Future[A]
}

private[persistence] class DefaultScalikeJDBCSessionProvider(
    poolName: String,
    implicit private val executor: ExecutionContext)
    extends ScalikeJDBCSessionProvider {

  override def withPool[A](f: (SharedAsyncDBSession) => Future[A]): Future[A] = {
    f(SharedAsyncDBSession(AsyncConnectionPool.borrow(poolName)))
  }

  override def localTx[A](f: (TxAsyncDBSession) => Future[A]): Future[A] = {
    AsyncConnectionPool
      .borrow(poolName)
      .toNonSharedConnection()
      .map { nonSharedConnection =>
        TxAsyncDBSession(nonSharedConnection)
      }
      .flatMap { tx =>
        AsyncTx.inTransaction[A](tx, f)
      }
  }
}
