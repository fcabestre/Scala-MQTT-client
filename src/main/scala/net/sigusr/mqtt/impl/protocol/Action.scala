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

import net.sigusr.mqtt.api.MQTTAPIMessage
import net.sigusr.mqtt.impl.frames.Frame

sealed trait Action

case class Sequence(actions : Seq[Action] = Nil) extends Action
case class SendToClient(message : MQTTAPIMessage) extends Action
case class SendToNetwork(frame : Frame) extends Action
case object CloseTransport extends Action
case class SetKeepAliveValue(timeout : Long) extends Action
case class StartTimer(timeout : Long) extends Action
case class SetPendingPingResponse(isPending : Boolean) extends Action


