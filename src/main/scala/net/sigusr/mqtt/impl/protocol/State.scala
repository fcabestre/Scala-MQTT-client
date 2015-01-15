package net.sigusr.mqtt.impl.protocol

import akka.actor.Cancellable
import net.sigusr.mqtt.api._

case class State(lastSentMessageTimestamp: Long = 0,
  isPingResponsePending: Boolean = false,
  keepAlive: Long = DEFAULT_KEEP_ALIVE.toLong,
  timerTask: Option[Cancellable] = None) {

  def setLastSentMessageTimestamp(lastMessageTimeStamp: Long): State = this.copy(lastSentMessageTimestamp = lastSentMessageTimestamp)

  def setTimeOut(keepAlive: Long): State = this.copy(keepAlive = keepAlive)

  def setPingResponsePending(isPingResponsePending: Boolean): State = this.copy(isPingResponsePending = isPingResponsePending)

  def setTimerTask(timerTask: Cancellable): State = this.copy(timerTask = Some(timerTask))

}

