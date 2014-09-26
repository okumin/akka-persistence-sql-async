package akka.persistence.snapshot.sqlasync

import akka.actor.{Props, ActorSystem}
import akka.persistence.SnapshotProtocol.{DeleteSnapshot, DeleteSnapshots, LoadSnapshot, LoadSnapshotResult, SaveSnapshot}
import akka.persistence._
import akka.persistence.snapshot.sqlasync.AsyncSnapshotStore.DeleteSnapshotFailure
import akka.persistence.snapshot.sqlasync.AsyncSnapshotStore.DeleteSnapshotSuccess
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import java.io.IOException
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.{Promise, Future}

class AsyncSnapshotStoreSpec
  extends TestKit(ActorSystem("async-snapshot-store-spec", ConfigFactory.load("akka-application.conf")))
  with ImplicitSender
  with WordSpecLike
  with Matchers {

  private[this] class TestSnapshotStore() extends AsyncSnapshotStore {
    override protected val publish: Boolean = true
    private[this] val error = Future.failed(new NotImplementedError("This method is not implemented."))
    override protected def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
      Future.successful(None)
    }
    override protected def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
      Future.successful(())
    }
    override protected def saved(metadata: SnapshotMetadata): Unit = {}
    override protected def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = error
    override protected def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = error
  }
  private[this] val persistenceId = "10"
  private[this] val sequenceNr = Long.MaxValue
  private[this] val maxTimestamp = Long.MaxValue
  private[this] val criteria = SnapshotSelectionCriteria(sequenceNr, maxTimestamp)

  "AsyncSnapshotStore" should {
    "reply LoadSnapshotResult if LoadSnapshot is received and no deletion is not being executed" in {
      val sut = system.actorOf(Props(new TestSnapshotStore))
      sut ! LoadSnapshot(persistenceId, criteria, sequenceNr)
      expectMsg(LoadSnapshotResult(None, sequenceNr))
    }

    "send DeleteSnapshot to self if SnapshotFailure is received" in {
      val metadata = SnapshotMetadata(persistenceId, sequenceNr, maxTimestamp)
      class SnapshotStore extends TestSnapshotStore {
        override protected def deleteAsync(_metadata: SnapshotMetadata): Future[Unit] = {
          require(_metadata == metadata)
          Future.successful(())
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[DeleteSnapshotSuccess])

      val sut = system.actorOf(Props(new SnapshotStore))
      val failure = SaveSnapshotFailure(metadata, new IOException())
      sut ! failure
      expectMsg(failure)
      probe.expectMsg(DeleteSnapshotSuccess(persistenceId, DeleteSnapshot(metadata)))
    }

    "reply LoadSnapshotResult after it succeeds to delete snapshot if LoadSnapshot is received and deletion is being executed" in {
      val metadata = SnapshotMetadata(persistenceId, sequenceNr, maxTimestamp)
      val promise = Promise[Unit]()
      class SnapshotStore extends TestSnapshotStore {
        override protected def deleteAsync(_metadata: SnapshotMetadata): Future[Unit] = {
          require(_metadata == metadata)
          promise.future
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[DeleteSnapshotSuccess])

      val command = DeleteSnapshot(metadata)
      val sut = system.actorOf(Props(new SnapshotStore))
      sut ! command

      sut ! LoadSnapshot(persistenceId, criteria, sequenceNr)
      sut ! LoadSnapshot(persistenceId, criteria, sequenceNr)
      expectNoMsg()

      promise.success(())
      probe.expectMsg(DeleteSnapshotSuccess(persistenceId, command))
      expectMsg(LoadSnapshotResult(None, sequenceNr))
      expectMsg(LoadSnapshotResult(None, sequenceNr))
    }

    "reply LoadSnapshotResult after it fails to delete snapshot if LoadSnapshot is received and deletion is being executed" in {
      val metadata = SnapshotMetadata(persistenceId, sequenceNr, maxTimestamp)
      val promise = Promise[Unit]()
      class SnapshotStore extends TestSnapshotStore {
        override protected def deleteAsync(_metadata: SnapshotMetadata): Future[Unit] = {
          require(_metadata == metadata)
          promise.future
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[DeleteSnapshotFailure])

      val command = DeleteSnapshot(metadata)
      val sut = system.actorOf(Props(new SnapshotStore))
      sut ! command

      sut ! LoadSnapshot(persistenceId, criteria, sequenceNr)
      sut ! LoadSnapshot(persistenceId, criteria, sequenceNr)
      expectNoMsg()

      val error = new IOException()
      promise.failure(error)
      probe.expectMsg(DeleteSnapshotFailure(persistenceId, command, error))
      expectMsg(LoadSnapshotResult(None, sequenceNr))
      expectMsg(LoadSnapshotResult(None, sequenceNr))
    }

    "reply SaveSnapshotSuccess after it succeeds to delete snapshot if SaveSnapshot is received and deletion with criteria is being executed" in {
      val metadata = SnapshotMetadata(persistenceId, sequenceNr, maxTimestamp)
      val promise = Promise[Unit]()
      class SnapshotStore extends TestSnapshotStore {
        override protected def deleteAsync(_persistenceId: String, _criteria: SnapshotSelectionCriteria): Future[Unit] = {
          require(_persistenceId == persistenceId)
          require(_criteria == criteria)
          promise.future
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[DeleteSnapshotSuccess])

      val command = DeleteSnapshots(persistenceId, criteria)
      val sut = system.actorOf(Props(new SnapshotStore))
      sut ! command

      sut ! SaveSnapshot(metadata, 10)
      sut ! SaveSnapshot(metadata, 11)
      expectNoMsg()

      promise.success(())
      probe.expectMsg(DeleteSnapshotSuccess(persistenceId, command))
      expectMsgPF() {
        case SaveSnapshotSuccess(SnapshotMetadata(pid, snr, _)) =>
          pid should be (persistenceId)
          snr should be (sequenceNr)
      }
      expectMsgPF() {
        case SaveSnapshotSuccess(SnapshotMetadata(pid, snr, _)) =>
          pid should be (persistenceId)
          snr should be (sequenceNr)
      }
    }

    "reply SaveSnapshotSuccess after it fails to delete snapshot if SaveSnapshot is received and deletion with criteria is being executed" in {
      val metadata = SnapshotMetadata(persistenceId, sequenceNr, maxTimestamp)
      val promise = Promise[Unit]()
      class SnapshotStore extends TestSnapshotStore {
        override protected def deleteAsync(_persistenceId: String, _criteria: SnapshotSelectionCriteria): Future[Unit] = {
          require(_persistenceId == persistenceId)
          require(_criteria == criteria)
          promise.future
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[DeleteSnapshotFailure])

      val command = DeleteSnapshots(persistenceId, criteria)
      val sut = system.actorOf(Props(new SnapshotStore))
      sut ! command

      sut ! SaveSnapshot(metadata, 10)
      sut ! SaveSnapshot(metadata, 11)
      expectNoMsg()

      val error = new IOException()
      promise.failure(error)
      probe.expectMsg(DeleteSnapshotFailure(persistenceId, command, error))
      expectMsgPF() {
        case SaveSnapshotSuccess(SnapshotMetadata(pid, snr, _)) =>
          pid should be (persistenceId)
          snr should be (sequenceNr)
      }
      expectMsgPF() {
        case SaveSnapshotSuccess(SnapshotMetadata(pid, snr, _)) =>
          pid should be (persistenceId)
          snr should be (sequenceNr)
      }
    }
  }
}
