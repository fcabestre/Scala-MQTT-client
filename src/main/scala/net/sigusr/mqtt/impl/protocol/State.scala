package net.sigusr.mqtt.impl.protocol

import akka.actor.{ ActorRef, Cancellable }
import net.sigusr.mqtt.api._

case class State(
    lastSentMessageTimestamp: Long = 0,
    isPingResponsePending: Boolean = false,
    keepAlive: Long = DEFAULT_KEEP_ALIVE.toLong,
    timerTask: Option[Cancellable] = None,
    client: ActorRef = null,
    tcpConnection: ActorRef = null) {
}

object State {
  def setLastSentMessageTimestamp(lastSentMessageTimeStamp: Long)(implicit state: State): State =
    state.copy(lastSentMessageTimestamp = lastSentMessageTimeStamp)

  def setTimeOut(keepAlive: Long)(implicit state: State): State =
    state.copy(keepAlive = keepAlive)

  def setPingResponsePending(isPingResponsePending: Boolean)(implicit state: State): State =
    state.copy(isPingResponsePending = isPingResponsePending)

  def setTimerTask(timerTask: Cancellable)(implicit state: State): State =
    state.copy(timerTask = Some(timerTask))

  def resetTimerTask(implicit state: State): State = {
    state.timerTask foreach { _.cancel() }
    state.copy(timerTask = None)
  }

  def setTCPManager(tcpManager: ActorRef)(implicit state: State): State =
    state.copy(tcpConnection = tcpManager)
}

