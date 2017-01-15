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

import net.sigusr.mqtt.SpecUtils._
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames._
import org.specs2.mutable.Specification
import scodec.bits.ByteVector

import scala.collection.immutable.{ TreeMap, TreeSet }
import scala.util.Random

object HandlersSpec extends Specification with Handlers {

  "The transportNotReady() function" should {
    "Define the action to perform when the transport is not ready" in {
      transportNotReady() shouldEqual SendToClient(ConnectionFailure(ServerNotResponding))
    }
  }

  "The connectionClosed() function" should {
    "Define the action to perform when the connection is closed" in {
      connectionClosed() shouldEqual SendToClient(Disconnected)
    }
  }

  "The handleApiConnect() function" should {
    "Define the action to perform to handle a Connect API message with session cleaning" in {
      val clientId = "client id"
      val keepAlive = 60
      val cleanSession = true
      val topic = Some("topic")
      val message = Some("message")
      val will = Will(retain = false, AtLeastOnce, "topic", "message")
      val user = Some("user")
      val password = Some("password")
      val input = Connect(clientId, keepAlive, cleanSession, Some(will), user, password)
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce.enum, willFlag = true, cleanSession, keepAlive)
      val publishFrame = PublishFrame(Header(), "topic", 42, ByteVector(0x01))
      val pubrecFrame = PubrecFrame(Header(), 19)
      val registers = Registers(inFlightSentFrame = TreeMap(42 → publishFrame, 19 → pubrecFrame))
      val expected = Sequence(Seq(
        SetKeepAlive(keepAlive.toLong * 1000),
        SendToNetwork(ConnectFrame(header, variableHeader, clientId, topic, message, user, password))
      ))
      handleApiConnect(input).eval(registers) should_== expected
    }

    "Define the action to perform to handle a Connect API message when there is in flight frames and no session cleaning" in {
      val clientId = "client id"
      val keepAlive = 60
      val cleanSession = false
      val topic = Some("topic")
      val message = Some("message")
      val will = Will(retain = false, AtLeastOnce, "topic", "message")
      val user = Some("user")
      val password = Some("password")
      val input = Connect(clientId, keepAlive, cleanSession, Some(will), user, password)

      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce.enum, willFlag = true, cleanSession, keepAlive)
      val connectFrame = ConnectFrame(header, variableHeader, clientId, topic, message, user, password)
      val publishFrame = PublishFrame(Header(dup = true), "topic", 42, ByteVector(0x01))
      val pubrelFrame = PubrelFrame(Header(dup = true, qos = AtMostOnce.enum), 19)
      val registers = Registers(inFlightSentFrame = TreeMap(42 → publishFrame, 19 → pubrelFrame))

      val expected = Sequence(Seq(
        SetKeepAlive(keepAlive.toLong * 1000),
        SendToNetwork(connectFrame),
        SendToNetwork(pubrelFrame),
        SendToNetwork(publishFrame)
      ))
      handleApiConnect(input).eval(registers) should_== expected
    }
  }

  "The handleApiCommand() function" should {

    "Define the action to perform to handle a Connect API message when already connected" in {
      val clientId = "client id"
      val keepAlive = 60
      val cleanSession = false
      val topic = Some("topic")
      val message = Some("message")
      val will = Will(retain = false, AtLeastOnce, "topic", "message")
      val user = Some("user")
      val password = Some("password")
      val input = Connect(clientId, keepAlive, cleanSession, Some(will), user, password)
      val expected = SendToClient(Error(AlreadyConnected))
      handleApiCommand(input).eval(Registers()) should_== expected
    }

    "Define the action to perform to handle a Disconnect API message" in {
      val input = Disconnect
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val expected = SendToNetwork(DisconnectFrame(header))
      handleApiCommand(input).eval(Registers()) should_== expected
    }

    "Define the action to perform to handle a Status API message" in {
      val input = Status
      SendToClient(Connected)
      val expected = SendToClient(Connected)
      handleApiCommand(input).eval(Registers()) should_== expected
    }

    "Define the action to perform to handle a Subscribe API message" in {
      val topicsInput = Vector(("topic0", AtMostOnce), ("topic1", ExactlyOnce), ("topic2", AtLeastOnce))
      val messageId = Random.nextInt(65536)
      val input = Subscribe(topicsInput, messageId)
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topicsResult = Vector(("topic0", AtMostOnce.enum), ("topic1", ExactlyOnce.enum), ("topic2", AtLeastOnce.enum))
      val expected = SendToNetwork(SubscribeFrame(header, messageId, topicsResult))
      handleApiCommand(input).eval(Registers()) should_== expected
    }

    "Define the action to perform to handle an Unsubscribe API message" in {
      val topicsInput = Vector("topic0")
      val messageId = Random.nextInt(65536)
      val input = Unsubscribe(topicsInput, messageId)
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topicsResult = Vector("topic0")
      val expected = SendToNetwork(UnsubscribeFrame(header, messageId, topicsResult))
      handleApiCommand(input).eval(Registers()) should_== expected
    }

    "Define the action to perform to handle a Publish API message with QoS of 'At most once'" in {
      val topic = "topic0"
      val qos = AtMostOnce
      val retain = true
      val payload = makeRandomByteVector(48)
      val messageId = Random.nextInt(65536)
      val input = Publish(topic, payload, qos, Some(messageId), retain)
      val header = Header(dup = false, qos.enum, retain)
      val expected = SendToNetwork(PublishFrame(header, topic, messageId, ByteVector(payload)))
      handleApiCommand(input).eval(Registers()) should_== expected
    }

    "Define the action to perform to handle a Publish API message with QoS of 'at least once' or 'exactly once'" in {
      val topic = "topic0"
      val qos = AtLeastOnce
      val retain = true
      val payload = makeRandomByteVector(32)
      val messageId = Random.nextInt(65536)
      val input = Publish(topic, payload, qos, Some(messageId), retain)
      val inputHeader = Header(dup = false, qos.enum, retain)
      val expectedHeader = Header(dup = true, qos.enum, retain)
      val expected =
        Sequence(
          Seq(
            StoreSentInFlightFrame(messageId, PublishFrame(expectedHeader, topic, messageId, ByteVector(payload))),
            SendToNetwork(PublishFrame(inputHeader, topic, messageId, ByteVector(payload)))
          )
        )
      handleApiCommand(input).eval(Registers()) should_== expected
    }
  }

  "The timerSignal() function" should {
    "Define the action to perform to handle a SendKeepAlive internal API message while not waiting for a ping response and messages were recently sent" in {
      val state = Registers(keepAlive = 30000, lastSentMessageTimestamp = 120000000, isPingResponsePending = false)
      val expected = StartPingRespTimer(29500)
      timerSignal(120000500).eval(state) should_== expected
    }

    "Define the action to perform to handle a SendKeepAlive internal API message while not waiting for a ping response but no messages were recently sent" in {
      val state = Registers(keepAlive = 30000, lastSentMessageTimestamp = 120000000, isPingResponsePending = false)
      val expected = Sequence(Seq(
        SetPendingPingResponse(isPending = true),
        StartPingRespTimer(30000),
        SendToNetwork(PingReqFrame(Header(dup = false, AtMostOnce.enum, retain = false)))
      ))
      timerSignal(120029001).eval(state) should_== expected
    }

    "Define the action to perform to handle a SendKeepAlive internal API message while waiting for a ping response" in {
      val state = Registers(keepAlive = 30000, lastSentMessageTimestamp = 120000000, isPingResponsePending = true)
      val expected = ForciblyCloseTransport
      timerSignal(120029999).eval(state) should_== expected
    }
  }

  "The handleNetworkFrames() function" should {

    "Provide no actions when the frame should not be handled" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val input = PingReqFrame(header)
      val state = Registers(keepAlive = 30000)
      val expected = ForciblyCloseTransport
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a ConnackFrame on a successful connection with keep alive greater than 0" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val input = ConnackFrame(header, 0)
      val state = Registers(keepAlive = 30000)
      val expected = Sequence(Seq(StartPingRespTimer(state.keepAlive), SendToClient(Connected)))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a ConnackFrame on a successful connection with keep alive equal to 0" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val input = ConnackFrame(header, 0)
      val state = Registers(keepAlive = 0)
      val expected = SendToClient(Connected)
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a ConnackFrame (failed connection)" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val reason = BadUserNameOrPassword
      val input = ConnackFrame(header, reason.enum)
      val state = Registers(keepAlive = 30000)
      val expected = SendToClient(ConnectionFailure(reason))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a PingRespFrame" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val input = PingRespFrame(header)
      val state = Registers(keepAlive = 30000)
      val expected = SetPendingPingResponse(isPending = false)
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a PublishFrame with a QoS of at most once" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val topic = "topic"
      val payload = makeRandomByteVector(64)
      val input = PublishFrame(header, topic, Random.nextInt(65536), ByteVector(payload))
      val state = Registers(keepAlive = 30000)
      val expected = SendToClient(Message(topic, payload))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a PublishFrame with a QoS of at least once" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topic = "topic"
      val payload = makeRandomByteVector(64)
      val messageId = Random.nextInt(65536)
      val input = PublishFrame(header, topic, messageId, ByteVector(payload))
      val state = Registers(keepAlive = 30000)
      val expected = Sequence(Seq(
        SendToClient(Message(topic, payload)),
        SendToNetwork(PubackFrame(header.copy(qos = 0), messageId))
      ))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle an already received PublishFrame with a QoS of exactly once" in {
      val header = Header(dup = false, ExactlyOnce.enum, retain = false)
      val topic = "topic"
      val payload = makeRandomByteVector(64)
      val messageId = Random.nextInt(65536)
      val input = PublishFrame(header, topic, messageId, ByteVector(payload))
      val state = Registers(keepAlive = 30000, inFlightRecvFrame = TreeSet(messageId))
      val expected = Sequence(Seq(
        SendToNetwork(PubrecFrame(header.copy(qos = 0), messageId))
      ))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a new PublishFrame with a QoS of exactly once" in {
      val header = Header(dup = false, ExactlyOnce.enum, retain = false)
      val topic = "topic"
      val payload = makeRandomByteVector(64)
      val messageId = Random.nextInt(65536)
      val input = PublishFrame(header, topic, messageId, ByteVector(payload))
      val state = Registers(keepAlive = 30000)
      val expected = Sequence(Seq(
        SendToClient(Message(topic, payload)),
        StoreRecvInFlightFrameId(messageId),
        SendToNetwork(PubrecFrame(header.copy(qos = 0), messageId))
      ))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a PubackFrame" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val messageId = Random.nextInt(65536)
      val input = PubackFrame(header, messageId)
      val state = Registers(keepAlive = 30000)
      val expected = Sequence(Seq(RemoveSentInFlightFrame(messageId), SendToClient(Published(messageId))))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a PubrecFrame" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val messageId = Random.nextInt(65536)
      val input = PubrecFrame(header, messageId)
      val state = Registers(keepAlive = 30000)
      val frame = PubrelFrame(header.copy(qos = 1), messageId)
      val expected =
        Sequence(
          Seq(
            RemoveSentInFlightFrame(messageId),
            StoreSentInFlightFrame(messageId, PubrelFrame(header.copy(dup = true, qos = 1), messageId)),
            SendToNetwork(frame)
          )
        )
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a PubrelFrame" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val messageId = Random.nextInt(65536)
      val input = PubrelFrame(header.copy(qos = 1), messageId)
      val state = Registers(keepAlive = 30000)
      val result = Sequence(
        Seq(
          RemoveRecvInFlightFrameId(messageId),
          SendToNetwork(PubcompFrame(header, messageId))
        )
      )
      handleNetworkFrames(input).eval(state) should_== result
    }

    "Define the actions to perform to handle a PubcompFrame" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val messageId = Random.nextInt(65536)
      val input = PubcompFrame(header, messageId)
      val state = Registers(keepAlive = 30000)
      val expected = Sequence(Seq(RemoveSentInFlightFrame(messageId), SendToClient(Published(messageId))))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle a SubackFrame" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val messageId = Random.nextInt(65536)
      val qosInput = Vector(AtLeastOnce.enum, ExactlyOnce.enum)
      val qosResult = Vector(AtLeastOnce, ExactlyOnce)
      val input = SubackFrame(header, messageId, qosInput)
      val state = Registers(keepAlive = 30000)
      val expected = SendToClient(Subscribed(qosResult, messageId))
      handleNetworkFrames(input).eval(state) should_== expected
    }

    "Define the actions to perform to handle an UnsubackFrame" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val messageId = Random.nextInt(65536)
      val input = UnsubackFrame(header, messageId)
      val state = Registers(keepAlive = 30000)
      val expected = SendToClient(Unsubscribed(messageId))
      handleNetworkFrames(input).eval(state) should_== expected
    }
  }
}
