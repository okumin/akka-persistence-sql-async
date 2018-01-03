package sample.serializer

import akka.actor._
import akka.persistence.serialization.{MessageSerializer, Snapshot, SnapshotSerializer}
import akka.persistence.{PersistentActor, PersistentRepr, SnapshotOffer}
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.util.control.NonFatal

case class PutEvent(key: String, value: String)
object PutEvent {
  implicit val format: Format[PutEvent] = {
    (
      (__ \ "key").format[String] and
        (__ \ "value").format[String]
    )(PutEvent.apply, unlift(PutEvent.unapply))
  }
}

sealed abstract class Command
case class Add(key: String, value: String) extends Command
case class Replace(key: String, value: String) extends Command

case object Print

case object SaveSnapshot

case class State(kvs: Map[String, String])
object State {
  implicit val format: Format[State] = {
    (__ \ "kvs").format[Map[String, String]].inmap[State](State.apply, _.kvs)
  }
}

/**
  * This is a sample that uses a custom serializer.
  * This sample is too simple, so read the document if you implement a custom serializer.
  * @see [[http://doc.akka.io/docs/akka/2.4.1/scala/persistence-schema-evolution.html]]
  */
class SamplePersistentActor extends PersistentActor with ActorLogging {
  override def persistenceId: String = "serializer-sample"

  var kvs: Map[String, String] = Map.empty

  override def receiveRecover: Receive = {
    case PutEvent(key, value)              => kvs += key -> value
    case SnapshotOffer(_, snapshot: State) => kvs = snapshot.kvs
    case x                                 => log.warning(x.toString)
  }

  override def receiveCommand: Receive = {
    case Add(key, value) if !kvs.contains(key) =>
      persist(PutEvent(key, value)) {
        case PutEvent(k, v) => kvs += k -> v
      }
    case Replace(key, value) if kvs.contains(key) =>
      persist(PutEvent(key, value)) {
        case PutEvent(k, v) => kvs += k -> v
      }
    case SaveSnapshot => saveSnapshot(State(kvs))
    case Print        => println(kvs)
    case x            => log.info(x.toString)
  }
}

class SamplePersistentReprSerializer(system: ExtendedActorSystem) extends Serializer {
  implicit private[this] val format: Format[PersistentRepr] = {
    (
      (__ \ "payload").format[PutEvent] and
        (__ \ "sequence_nr").format[Long] and
        (__ \ "persistence_id").format[String] and
        (__ \ "manifest").format[String] and
        (__ \ "deleted").format[Boolean] and
        (__ \ "writer_uuid").format[String]
    )(PersistentRepr(_, _, _, _, _, null, _),
      x =>
        (x.payload.asInstanceOf[PutEvent],
         x.sequenceNr,
         x.persistenceId,
         x.manifest,
         x.deleted,
         x.writerUuid))
  }

  private[this] val serializer: Serializer = new MessageSerializer(system)

  override def identifier: Int = 1024

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    try {
      Json.fromJson[PersistentRepr](Json.parse(bytes)).get
    } catch {
      case NonFatal(_) => serializer.fromBinary(bytes, manifest)
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case p @ PersistentRepr(_: PutEvent, _) =>
      Json.toJson(p).toString().getBytes(StandardCharsets.UTF_8)
    case x => serializer.toBinary(x)
  }
}

class SampleSnapshotSerializer(system: ExtendedActorSystem) extends Serializer {
  private[this] val serializer = new SnapshotSerializer(system)

  override val identifier: Int = 2048

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    try {
      Snapshot(Json.fromJson[State](Json.parse(bytes)).get)
    } catch {
      case NonFatal(_) => serializer.fromBinary(bytes, manifest)
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case Snapshot(s: State) => Json.toJson(s).toString().getBytes(StandardCharsets.UTF_8)
    case x                  => serializer.toBinary(x)
  }
}

object SerializerSample {
  val config = ConfigFactory.load("serializer-sample.conf")
  val system = ActorSystem("serializer-sample", config)

  val persistentActor = system.actorOf(Props[SamplePersistentActor])
}
