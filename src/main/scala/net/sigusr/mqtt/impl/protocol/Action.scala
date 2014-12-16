package net.sigusr.mqtt.impl.protocol

import net.sigusr.mqtt.api.MQTTAPIMessage
import net.sigusr.mqtt.impl.frames.Frame

import scala.concurrent.duration.FiniteDuration

sealed trait Action

case object Nothing extends Action
case class SendToClient(message : MQTTAPIMessage) extends Action
case class SendToNetwork(frame : Frame) extends Action
case object CloseTransport extends Action
case class SetKeepAliveValue(duration : FiniteDuration) extends Action
case object StartKeepAliveTimer extends Action
case object StartPingResponseTimer extends Action
case object CancelPingResponseTimer extends Action


