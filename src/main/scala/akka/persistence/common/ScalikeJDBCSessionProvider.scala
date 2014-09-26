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

private[persistence] class DefaultScalikeJDBCSessionProvider(poolName: String, implicit private val executor: ExecutionContext)
  extends ScalikeJDBCSessionProvider {

  override def withPool[A](f: (SharedAsyncDBSession) => Future[A]): Future[A] = {
    f(SharedAsyncDBSession(AsyncConnectionPool.borrow(poolName)))
  }

  override def localTx[A](f: (TxAsyncDBSession) => Future[A]): Future[A] = {
    AsyncConnectionPool
      .borrow(poolName)
      .toNonSharedConnection()
      .map { nonSharedConnection => TxAsyncDBSession(nonSharedConnection) }
      .flatMap { tx => AsyncTx.inTransaction[A](tx, f) }
  }
}
