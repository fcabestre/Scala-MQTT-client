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
  def setLastSentMessageTimestamp(lastSentMessageTimeStamp: Long, state: State): State =
    state.copy(lastSentMessageTimestamp = lastSentMessageTimeStamp)

  def setTimeOut(keepAlive: Long, state: State): State =
    state.copy(keepAlive = keepAlive)

  def setPingResponsePending(isPingResponsePending: Boolean, state: State): State =
    state.copy(isPingResponsePending = isPingResponsePending)

  def setTimerTask(timerTask: Cancellable, state: State): State =
    state.copy(timerTask = Some(timerTask))

  def resetTimerTask(state: State): State = {
    state.timerTask foreach { _.cancel() }
    state.copy(timerTask = None)
  }

  def setTCPManager(tcpManager: ActorRef, state: State): State =
    state.copy(tcpConnection = tcpManager)
}

