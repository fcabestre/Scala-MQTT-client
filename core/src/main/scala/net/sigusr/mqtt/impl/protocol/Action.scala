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

import net.sigusr.mqtt.api.APIResponse
import net.sigusr.mqtt.impl.frames.Frame

private[protocol] sealed trait Action

private[protocol] case class Sequence(actions: Seq[Action] = Nil) extends Action
private[protocol] case class SendToClient(message: APIResponse) extends Action
private[protocol] case class SendToNetwork(frame: Frame) extends Action
private[protocol] case object ForciblyCloseTransport extends Action
private[protocol] case class SetKeepAlive(keepAlive: Long) extends Action
private[protocol] case class StartPingRespTimer(timeout: Long) extends Action
private[protocol] case class SetPendingPingResponse(isPending: Boolean) extends Action
private[protocol] case class StoreSentInFlightFrame(id: Int, frame: Frame) extends Action
private[protocol] case class RemoveSentInFlightFrame(id: Int) extends Action
private[protocol] case class StoreRecvInFlightFrameId(id: Int) extends Action
private[protocol] case class RemoveRecvInFlightFrameId(id: Int) extends Action

