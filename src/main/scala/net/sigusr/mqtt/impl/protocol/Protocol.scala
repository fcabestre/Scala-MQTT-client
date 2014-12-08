/*
 * Copyright 2014 Frédéric Cabestre
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor._
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.{ConnackFrame, ConnectFrame, DisconnectFrame, _}
import net.sigusr.mqtt.impl.protocol.Transport.{PingRespTimeout, SendKeepAlive, InternalAPIMessage}
import scodec.bits.ByteVector
import scala.concurrent.duration._

abstract class Protocol(client: ActorRef, mqttBrokerAddress: InetSocketAddress) extends Actor with Transport with ActorLogging {

  var keepAliveTask: Option[Cancellable] = None
  var keepAliveInterval: Option[Int] = None
  var keepAliveResponseInterval: Option[Cancellable] = None
  var messageCounter = 0
  var pubMap = Map[Int, Int]()

  initTransport(mqttBrokerAddress)
  import context.dispatcher

  def handleApiMessages(apiMessage : MQTTAPIMessage) : Option[Frame] = apiMessage match {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      keepAliveInterval = Some(keepAlive)
      val header = Header(dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      Some(ConnectFrame(header, variableHeader, clientId, topic, message, user, password))
    case MQTTDisconnect =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      Some(DisconnectFrame(header))
    case MQTTPublish(topic, qos, retain, payload, exchangeId) =>
      val header = Header(dup = false, qos, retain)
      messageCounter = (messageCounter + 1) % 65535
      if (qos == AtLeastOnce || qos == ExactlyOnce) {
        exchangeId foreach { id => pubMap += (messageCounter -> id) }
      } else {
        // according to spec, if QOS == AtMostOnce, then the server will not send an Ack
        sender ! MQTTPublishSuccess(exchangeId)
      }
      Some(PublishFrame(header, topic, MessageIdentifier(messageCounter), ByteVector(payload)))
    case _ => None
  }

  def handleNetworkFrames(frame : Frame) : Option[MQTTAPIMessage] = frame match {
    case ConnackFrame(header, connackVariableHeader) =>
      // side effect - start the keepAlive timer
      keepAliveInterval foreach { k => // TODO - reset this task upon receiving another API message, so as to not oversend PingRequests
        keepAliveTask = Some(context.system.scheduler.schedule(k.seconds, k.seconds, self, SendKeepAlive))
      }
      Some(MQTTConnected)
    case PingRespFrame(header) =>
      keepAliveResponseInterval foreach { k => k.cancel() }
      None
    case PubackFrame(header, messageIdentifier) =>
      if (pubMap.contains(messageIdentifier.identifier)) {
        val exId = pubMap get messageIdentifier.identifier
        pubMap -= messageIdentifier.identifier
        Some(MQTTPublishSuccess(exId))
      } else {
        Some(MQTTPublishSuccess(None))
      }
    case _ => None
  }

  def handleInternalApiMessages(apiMessage: InternalAPIMessage): Unit = apiMessage match {
    case SendKeepAlive => timeOut()
    case PingRespTimeout =>
      // TODO - is this the best way to flush the connection?
      context.stop(self)
    case _ =>
  }

  def disconnected() : Unit = {
    client ! MQTTDisconnected
  }

  def messageToSend(message: MQTTAPIMessage) : Unit = {
    handleApiMessages(message).map(sendFrameToNetwork).getOrElse(client ! MQTTWrongClientMessage)
  }

  def sendFrameToNetwork(frame : Frame): Unit = {
    self ! frame
  }

  def frameReceived(frame: Frame) : Unit = {
    handleNetworkFrames(frame).map(client ! _).getOrElse(())
  }

  def transportReady() : Unit = {
    client ! MQTTReady
  }

  def timeOut() : Unit = {
    // we want to terminate the connection if we don't receive a PingResp in a 'reasonable amount of time'
    keepAliveResponseInterval = Some(context.system.scheduler.scheduleOnce(5.seconds, self, PingRespTimeout ))
    self ! PingReqFrame(Header(dup = false, AtMostOnce, retain = false))
  }

  def transportNotReady() : Unit = {
    client ! MQTTNotReady
  }

  override def postStop(): Unit = {
    keepAliveTask foreach { k => k.cancel() }
    keepAliveResponseInterval foreach { k=> k.cancel() }
  }
}

