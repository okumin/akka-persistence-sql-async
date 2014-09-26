package akka.persistence.snapshot.sqlasync

import akka.actor.{Actor, ActorLogging}
import akka.dispatch.Envelope
import akka.pattern.pipe
import akka.persistence.SnapshotProtocol._
import akka.persistence._
import akka.persistence.snapshot.sqlasync.AsyncSnapshotStore.{DeleteSnapshotFailure, DeleteSnapshotSuccess}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Async version of [[akka.persistence.snapshot.SnapshotStore]].
 * Deletion by ScalikeJDBC-async is executed asynchronously,
 * so it is possible that user sends LoadSnapshot before completing to delete.
 * In that case, AsyncSnapshotStore enqueue messages to pending queue, and it will send after the deletion.
 */
private[sqlasync] trait AsyncSnapshotStore extends Actor with ActorLogging {

  import context.dispatcher

  // persistence id -> Envelopes
  private[this] val pendingMessages: mutable.Map[String, ListBuffer[Envelope]] = mutable.HashMap.empty
  // If a list is found, that means this is deleting some snapshots.
  private[this] def executeOrPending(persistenceId: String, message: Any)(f: => Unit): Unit = {
    pendingMessages.get(persistenceId) match {
      case Some(list) => list += Envelope(message, sender(), context.system)
      case None => f
    }
  }
  private[this] def retryPendingCommands(persistenceId: String): Unit = {
    pendingMessages.get(persistenceId).foreach(_.foreach {
      case Envelope(message, replyTo) => self.tell(message, replyTo)
    })
    pendingMessages.remove(persistenceId)
  }

  protected def publish: Boolean

  final def receive = {
    case m @ LoadSnapshot(persistenceId, criteria, toSequenceNr) =>
      executeOrPending(persistenceId, m) {
        val p = sender()
        loadAsync(persistenceId, criteria.limit(toSequenceNr)) map {
          sso ⇒ LoadSnapshotResult(sso, toSequenceNr)
        } recover {
          case e ⇒ LoadSnapshotResult(None, toSequenceNr)
        } pipeTo p
      }
    case m @ SaveSnapshot(metadata, snapshot) =>
      executeOrPending(metadata.persistenceId, m) {
        val p = sender()
        val md = metadata.copy(timestamp = System.currentTimeMillis)
        saveAsync(md, snapshot) map {
          _ ⇒ SaveSnapshotSuccess(md)
        } recover {
          case e ⇒ SaveSnapshotFailure(metadata, e)
        } to (self, p)
      }
    case evt @ SaveSnapshotSuccess(metadata) =>
      saved(metadata)
      sender ! evt // sender is persistentActor
    case evt @ SaveSnapshotFailure(metadata, _) =>
      self ! DeleteSnapshot(metadata)
      sender ! evt // sender is persistentActor
    case d @ DeleteSnapshot(metadata) =>
      val persistenceId = metadata.persistenceId
      executeOrPending(persistenceId, d) {
        pendingMessages.update(persistenceId, ListBuffer.empty)
        deleteAsync(metadata).onComplete {
          case Success(()) => self ! DeleteSnapshotSuccess(persistenceId, d)
          case Failure(e) => self ! DeleteSnapshotFailure(persistenceId, d, e)
        }
      }
    case d @ DeleteSnapshots(persistenceId, criteria) =>
      executeOrPending(persistenceId, d) {
        pendingMessages.update(persistenceId, ListBuffer.empty)
        deleteAsync(persistenceId, criteria).onComplete {
          case Success(()) =>
            self ! DeleteSnapshotSuccess(persistenceId, d)
          case Failure(e) =>
            self ! DeleteSnapshotFailure(persistenceId, d, e)
        }
      }
    case d @ DeleteSnapshotSuccess(persistenceId, message: AnyRef) =>
      retryPendingCommands(persistenceId)
      if (publish) {
        context.system.eventStream.publish(message)
        context.system.eventStream.publish(d)
      }
    case d @ DeleteSnapshotFailure(persistenceId, message, error) =>
      retryPendingCommands(persistenceId)
      log.error(s"Failed to delete snapshot, $message", error)
      if (publish) {
        log.error(s"Failed to delete snapshot, $message", error)
        context.system.eventStream.publish(d)
      }
  }

  protected def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]]

  protected def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit]

  protected def saved(metadata: SnapshotMetadata): Unit

  protected def deleteAsync(metadata: SnapshotMetadata): Future[Unit]

  protected def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit]
}

private object AsyncSnapshotStore {
  case class DeleteSnapshotSuccess(persistenceId: String, message: AnyRef)
  case class DeleteSnapshotFailure(persistenceId: String, message: AnyRef, error: Throwable)
}
