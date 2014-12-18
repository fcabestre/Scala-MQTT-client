package net.sigusr.mqtt.impl.protocol

import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames._
import net.sigusr.mqtt.impl.protocol.Transport.{PingRespTimeout, SendKeepAlive}
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

class ProtocolSpec extends Specification with Protocol with NoTimeConversions {

  sequential

  "The transportNotReady() function" should {
    "Define the action to perform when the transport is not ready" in {
      transportNotReady() shouldEqual SendToClient(MQTTNotReady)
    }
  }

  "The transportReady() function" should {
    "Define the action to perform when the transport is ready" in {
      transportReady() shouldEqual SendToClient(MQTTReady)
    }
  }

  "The connectionClosed() function" should {
    "Define the action to perform when the connection is closed" in {
      connectionClosed() shouldEqual SendToClient(MQTTDisconnected)
    }
  }

  "The handleApiMessages() function" should {
    "Define the action to perform to handle a MQTTConnect API message" in {
      val clientId = "client id"
      val keepAlive = 60
      val cleanSession = false
      val topic = Some("topic")
      val message = Some("message")
      val user = Some("user")
      val password = Some("password")
      val input = MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password)
      val header = Header(dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      val result =List(
        SetKeepAliveValue(keepAlive seconds),
        SendToNetwork(ConnectFrame(header, variableHeader, clientId, topic, message, user, password)))
      handleApiMessages(input) should_== result
    }

    "Define the action to perform to handle a MQTTDisconnect API message" in {
      val input = MQTTDisconnect
      val header = Header(dup = false, AtMostOnce, retain = false)
      List(SendToNetwork(DisconnectFrame(header)))
      val result =List(SendToNetwork(DisconnectFrame(header)))
      handleApiMessages(input) should_== result
    }

    "Define the action to perform to handle a MQTTSubscribe API message" in {
      val topics = Vector(("topic0", AtMostOnce), ("topic1", ExactlyOnce), ("topic2", AtLeastOnce))
      val exchangeId = Some(Random.nextInt(65535))
      val input = MQTTSubscribe(topics, exchangeId)
      val header = Header(dup = false, AtLeastOnce, retain = false)
      messageCounter = 5
      val result = List(SendToNetwork(SubscribeFrame(header, MessageIdentifier(messageCounter + 1), topics)))
      handleApiMessages(input) should_== result
    }

    "Define the action to perform to handle a MQTTPublish API message with QoS of 'At most once'" in {
      val topic = "topic0"
      val qos = AtMostOnce
      val retain = true
      val payload = Array[Byte]()
      Random.nextBytes(payload)
      val exchangeId = Some(Random.nextInt(65535))
      val input = MQTTPublish(topic, qos, retain, payload, exchangeId)
      val header = Header(dup = false, qos, retain)
      messageCounter = 42
      val result = List(
        SendToClient(MQTTPublishSuccess(exchangeId)),
        SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageCounter + 1), ByteVector(payload))))
      handleApiMessages(input) should_== result
    }

    "Define the action to perform to handle a MQTTPublish API message with QoS of 'at least once' or 'exactly once'" in {
      val topic = "topic0"
      val qos = AtLeastOnce
      val retain = true
      val payload = Array[Byte]()
      Random.nextBytes(payload)
      val exchangeId = Some(Random.nextInt(65535))
      val input = MQTTPublish(topic, qos, retain, payload, exchangeId)
      val header = Header(dup = false, qos, retain)
      messageCounter = 42
      val result = List(SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageCounter + 1), ByteVector(payload))))
      handleApiMessages(input) should_== result
    }

    "Define the action to perform to handle an API message that should not be sent by the user" in {
      val input = MQTTReady
      val result = List(SendToClient(MQTTWrongClientMessage))
      handleApiMessages(input) should_== result
    }

  }

  "The incrMessageCounter() function" should {
    "Increment the message counter by one" in {
      val initalValue = Random.nextInt(65534)
      messageCounter = initalValue
      incrMessageCounter should_== initalValue + 1
    }

    "Reset the counter to 1 when starting from 65535" in {
      messageCounter = 65535
      incrMessageCounter should_== 1
    }
  }

  "The handleInternalApiMessages() function" should {
    "Define the action to perform to handle a SendKeepAlive internal API message" in {
      val input = SendKeepAlive
      val result = List(
        StartPingResponseTimer,
        SendToNetwork(PingReqFrame(Header(dup = false, AtMostOnce, retain = false))))
      handleInternalApiMessages(input) should_== result
    }

    "Define the action to perform to handle a PingRespTimeout internal API message" in {
      val input = PingRespTimeout
      val result = List(CloseTransport)
      handleInternalApiMessages(input) should_== result
    }
  }

  "The handleNetworkFrames() function" should {
    "Define the actions to perform to handle a ConnackFrame" in {
      val header = Header(dup = false, AtLeastOnce, retain = false)
      val connackVariableHeader = ConnackVariableHeader(ConnectionRefused4)
      val input = ConnackFrame(header, connackVariableHeader)
      handleNetworkFrames(input) should_== List(StartKeepAliveTimer, SendToClient(MQTTConnected))
    }

    "Define the actions to perform to handle a PingRespFrame" in {
      val header = Header(dup = false, AtLeastOnce, retain = false)
      val input = PingRespFrame(header)
      handleNetworkFrames(input) should_== List(CancelPingResponseTimer)
    }
  }
}
