package net.sigusr.mqtt.impl.protocol

import akka.actor.{ Actor, ActorContext, ActorRef, Cancellable }
import net.sigusr.mqtt.api._

case class Registers(
    lastSentMessageTimestamp: Long = 0,
    isPingResponsePending: Boolean = false,
    keepAlive: Long = DEFAULT_KEEP_ALIVE.toLong,
    timerTask: Option[Cancellable] = None,
    client: ActorRef = null,
    tcpManager: ActorRef = null) {
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

  def setTCPManager(tcpManager: ActorRef) = modify[Registers](_.copy(tcpManager = tcpManager))

  def sendToTcpManager(command: Command)(implicit sender: ActorRef = Actor.noSender) = gets[Registers, Unit](_.tcpManager ! command)

  def sendToClient(response: APIResponse)(implicit sender: ActorRef = Actor.noSender) = gets[Registers, Unit](_.client ! response)

  def watchTcpManager(implicit context: ActorContext) = gets[Registers, ActorRef](context watch _.tcpManager)

  def unwatchTcpManager(implicit context: ActorContext) = gets[Registers, ActorRef](context unwatch _.tcpManager)
}