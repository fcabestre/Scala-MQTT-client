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

package org.ultimo.client

sealed trait MQTTAPIMessage

case object MQTTNotReady extends MQTTAPIMessage
case object MQTTReady extends MQTTAPIMessage
case class MQTTConnect(clientId : String,
                   keepAlive : Int = 30,
                   cleanSession : Boolean = true,
                   topic : Option[String] = None,
                   message : Option[String] = None,
                   user : Option[String] = None,
                   password : Option[String] = None) extends MQTTAPIMessage

case object MQTTConnected extends MQTTAPIMessage
case class MQTTConnectionFailure(reason : MQTTConnectionFailureReason) extends MQTTAPIMessage
case object MQTTDisconnect extends MQTTAPIMessage
case object MQTTDisconnected extends MQTTAPIMessage

sealed trait MQTTConnectionFailureReason
case object BadProtocolVersion extends MQTTConnectionFailureReason
case object IdentifierRejected extends MQTTConnectionFailureReason
case object ServerUnavailable extends MQTTConnectionFailureReason
case object BadUserNameOrPassword extends MQTTConnectionFailureReason
case object NotAuthorized extends MQTTConnectionFailureReason