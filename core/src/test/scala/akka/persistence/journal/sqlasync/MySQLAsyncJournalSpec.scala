package akka.persistence.journal.sqlasync

import akka.actor.Actor
import akka.persistence.JournalProtocol.{WriteMessageRejected, WriteMessages, WriteMessagesSuccessful}
import akka.persistence.helper.MySQLInitializer
import akka.persistence.journal.JournalSpec
import akka.persistence.{AtomicWrite, CapabilityFlag, PersistentImpl, PersistentRepr}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import java.io.NotSerializableException
import scala.concurrent.duration._

class MySQLAsyncJournalSpec
  extends JournalSpec(ConfigFactory.load("mysql-application.conf"))
  with MySQLInitializer {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  "ScalikeJDBCWriteJournal" must {
    "not execute SQL when all the events is not serializable" in {
      val probe = TestProbe()

      val notSerializableEvent = new Object { override def toString = "not serializable" }
      val messages = (6 to 8).map { i =>
        AtomicWrite(PersistentRepr(
          payload = notSerializableEvent,
          sequenceNr = i,
          persistenceId = pid,
          sender = Actor.noSender,
          writerUuid = writerUuid
        ))
      }
      journal ! WriteMessages(messages, probe.ref, actorInstanceId)
      probe.expectMsg(WriteMessagesSuccessful)

      val Pid = pid
      val WriterUuid = writerUuid
      probe.expectMsgPF() {
        case WriteMessageRejected(PersistentImpl(payload, 6L, Pid, _, _, Actor.noSender, WriterUuid), cause, _) =>
          payload should be(notSerializableEvent)
          cause.isInstanceOf[NotSerializableException] should be(true)
      }
      probe.expectMsgPF() {
        case WriteMessageRejected(PersistentImpl(payload, 7L, Pid, _, _, Actor.noSender, WriterUuid), cause, _) =>
          payload should be(notSerializableEvent)
          cause.isInstanceOf[NotSerializableException] should be(true)
      }
      probe.expectMsgPF() {
        case WriteMessageRejected(PersistentImpl(payload, 8L, Pid, _, _, Actor.noSender, WriterUuid), cause, _) =>
          payload should be(notSerializableEvent)
          cause.isInstanceOf[NotSerializableException] should be(true)
      }
      probe.expectNoMessage(1.second)
    }
  }
}
