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

abstract class Protocol(client: ActorRef, mqttBrokerAddress: InetSocketAddress) extends Actor with Transport with ActorLogging {

  initTransport(mqttBrokerAddress)

  def handleApiMessages(apiMessage : MQTTAPIMessage) : Option[Frame] = apiMessage match {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      Some(ConnectFrame(header, variableHeader, clientId, topic, message, user, password))
    case MQTTDisconnect =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      Some(DisconnectFrame(header))
    case _ => None
  }

  def handleNetworkFrames(frame : Frame) : Option[MQTTAPIMessage] = frame match {
    case ConnackFrame(header, connackVariableHeader) => Some(MQTTConnected)
    case _ => None
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
    self ! PingReqFrame(Header(dup = false, AtMostOnce, retain = false))
  }

  def transportNotReady() : Unit = {
    client ! MQTTNotReady
  }
}

