package net.sigusr.mqtt.impl.protocol

import akka.actor.{ Actor, ActorContext, ActorRef, Cancellable }
import akka.util.ByteString
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.Frame
import scodec.bits.BitVector

import scala.collection.immutable.{ TreeMap, TreeSet }

case class Registers(
    lastSentMessageTimestamp: Long = 0,
    isPingResponsePending: Boolean = false,
    keepAlive: Long = DEFAULT_KEEP_ALIVE.toLong,
    timerTask: Option[Cancellable] = None,
    client: ActorRef = null,
    inFlightSentFrame: TreeMap[Int, Frame] = TreeMap.empty[Int, Frame],
    inFlightRecvFrame: TreeSet[Int] = TreeSet.empty[Int],
    remainingBytes: BitVector = BitVector.empty,
    tcpManager: ActorRef = null
) {
}

object Registers {

  import akka.io.Tcp.Command

  import scalaz.State
  import scalaz.State._

  type RegistersState[A] = State[Registers, A]

  def setLastSentMessageTimestamp(lastSentMessageTimeStamp: Long) =
    modify[Registers](_.copy(lastSentMessageTimestamp = lastSentMessageTimeStamp))

  def setTimeOut(keepAlive: Long) = modify[Registers](_.copy(keepAlive = keepAlive))

  def setPingResponsePending(isPingResponsePending: Boolean) =
    modify[Registers](_.copy(isPingResponsePending = isPingResponsePending))

  def setTimerTask(timerTask: Cancellable) = modify[Registers](_.copy(timerTask = Some(timerTask)))

  def resetTimerTask = modify[Registers] { r ⇒
    r.timerTask foreach { _.cancel() }
    r.copy(timerTask = None)
  }

  def storeInFlightSentFrame(id: Int, frame: Frame) = modify[Registers] { r ⇒
    r.copy(inFlightSentFrame = r.inFlightSentFrame.updated(id, frame))
  }

  def removeInFlightSentFrame(id: Int) = modify[Registers] { r ⇒
    r.copy(inFlightSentFrame = r.inFlightSentFrame.drop(id))
  }

  def storeInFlightRecvFrame(id: Int) = modify[Registers] { r ⇒
    r.copy(inFlightRecvFrame = r.inFlightRecvFrame.insert(id))
  }

  def removeInFlightRecvFrame(id: Int) = modify[Registers] { r ⇒
    r.copy(inFlightRecvFrame = r.inFlightRecvFrame.drop(id))
  }

  def getRemainingBits(bytes: ByteString) = gets[Registers, BitVector](_.remainingBytes ++ BitVector.view(bytes.toArray))

  def setRemainingBits(bits: BitVector) = modify[Registers](_.copy(remainingBytes = bits))

  def setTCPManager(tcpManager: ActorRef) = modify[Registers](_.copy(tcpManager = tcpManager))

  def sendToTcpManager(command: Command)(implicit sender: ActorRef = Actor.noSender) = gets[Registers, Unit](_.tcpManager ! command)

  def sendToClient(response: APIResponse)(implicit sender: ActorRef = Actor.noSender) = gets[Registers, Unit](_.client ! response)

  def watchTcpManager(implicit context: ActorContext) = gets[Registers, ActorRef](context watch _.tcpManager)

  def unwatchTcpManager(implicit context: ActorContext) = gets[Registers, ActorRef](context unwatch _.tcpManager)
}