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

  def apiMessage2Frame(apiMessage : MQTTAPIMessage) : Frame = apiMessage match {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      ConnectFrame(header, variableHeader, clientId, topic, message, user, password)
    case MQTTDisconnect =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      DisconnectFrame(header)
  }

  def frame2ApiMessage(frame : Frame) : MQTTAPIMessage = frame match {
    case ConnackFrame(header, connackVariableHeader) => MQTTConnected
  }

  def disconnected() : Unit = {
    client ! MQTTDisconnected
  }

  def messageToSend(message: MQTTAPIMessage) : Unit = {
    val frame: Frame = apiMessage2Frame(message)
    self ! frame
  }

  def frameReceived(frame: Frame) : Unit = {
    val message: MQTTAPIMessage = frame2ApiMessage(frame)
    client ! message
  }

  def transportReady() : Unit = {
    client ! MQTTReady
  }

  def transportNotReady() : Unit = {
    client ! MQTTNotReady
  }
}

