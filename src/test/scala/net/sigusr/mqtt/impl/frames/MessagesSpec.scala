package net.sigusr.mqtt.impl.frames

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

import net.sigusr.mqtt.api.{MQTTMessageId, _}
import org.specs2.mutable._

object MessagesSpec extends Specification {

  "Quality of service" should {
    "Provide their «enum» value" in {
      AtMostOnce.enum should be_==(0)
      AtLeastOnce.enum should be_==(1)
      ExactlyOnce.enum should be_==(2)
    }

    "Be constructable from their corresponding «enum» value" in {
      import net.sigusr.mqtt.api.QualityOfService._

      fromEnum(-1) should throwA[IllegalArgumentException]
      fromEnum(0) should_=== AtMostOnce
      fromEnum(1) should_=== AtLeastOnce
      fromEnum(2) should_=== ExactlyOnce
      fromEnum(3) should throwA[IllegalArgumentException]
    }
  }

  "Connect variable header" should {
    "Have a valid keep alive timer" in {
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = false, userNameFlag = true, keepAliveTimer = -1)  should throwA[IllegalArgumentException]
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = false, userNameFlag = true, keepAliveTimer = 65536)  should throwA[IllegalArgumentException]
    }
    "Have a valid combination of username and password flags" in {
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = true, userNameFlag = false, keepAliveTimer = 0)  should throwA[IllegalArgumentException]
    }
  }

  "Connect failure reason" should {
    "Provide their «enum» value" in {
      BadProtocolVersion.enum should be_==(1)
      IdentifierRejected.enum should be_==(2)
      ServerUnavailable.enum should be_==(3)
      BadUserNameOrPassword.enum should be_==(4)
      NotAuthorized.enum should be_==(5)
    }

    "Be constructable from their corresponding «enum» value" in {
      import net.sigusr.mqtt.api.MQTTConnectionFailureReason._

      fromEnum(0) should throwA[IllegalArgumentException]
      fromEnum(1) should_=== BadProtocolVersion
      fromEnum(2) should_=== IdentifierRejected
      fromEnum(3) should_=== ServerUnavailable
      fromEnum(4) should_=== BadUserNameOrPassword
      fromEnum(5) should_=== NotAuthorized
      fromEnum(6) should throwA[IllegalArgumentException]
    }
  }

  "MessageIdentifier" should {
    "Check the range of the integer provided to its constructor" in {
      MQTTMessageId(-1) should throwA[IllegalArgumentException]
      MQTTMessageId(0) should not (throwA[IllegalArgumentException])
      MQTTMessageId(65535) should not (throwA[IllegalArgumentException])
      MQTTMessageId(65536) should throwA[IllegalArgumentException]
    }

    "Allow pattern matching" in {
      MQTTMessageId(42) match {
        case MQTTMessageId(i) => i should_=== 42
      }
    }

    "Have a literal syntax" in {
      val four = 4
      val two = 2
      mi"$four$two" should_=== MQTTMessageId(42)
      mi"42" should_=== MQTTMessageId(42)
      mi"-1" should throwA[IllegalArgumentException]
      mi"65536" should throwA[IllegalArgumentException]
      mi"fortytwo" should throwA[NumberFormatException]
    }

    "Have a implicit conversion from Int" in {
      def id(messageIdentifier: MQTTMessageId) : MQTTMessageId = messageIdentifier
      id(42) should_=== MQTTMessageId(42)
      id(-1) should throwA[IllegalArgumentException]
      id(65536) should throwA[IllegalArgumentException]
    }
  }

  "MQTTConnect" should {
    "Check the range of the keep alive value provided to its constructor" in {
      import net.sigusr.mqtt.api.MQTTConnect
      MQTTConnect("Client", keepAlive = -1) should throwA[IllegalArgumentException]
      MQTTConnect("Client", keepAlive = 0) should not throwA()
      MQTTConnect("Client", keepAlive = 65635) should not throwA()
      MQTTConnect("Client", keepAlive = 65636) should throwA[IllegalArgumentException]
    }
  }

  "MQTTPublish" should {
    "Have a valid combination of QoS and message identifier" in {
      import net.sigusr.mqtt.api.MQTTPublish
      MQTTPublish("topic", Vector(0x00), AtMostOnce) should not throwA()
      MQTTPublish("topic", Vector(0x00), AtLeastOnce) should throwA[IllegalArgumentException]
      MQTTPublish("topic", Vector(0x00), AtLeastOnce, Some(1)) should not throwA()
      MQTTPublish("topic", Vector(0x00), ExactlyOnce) should throwA[IllegalArgumentException]
      MQTTPublish("topic", Vector(0x00), ExactlyOnce, Some(1)) should not throwA()
    }
  }
}
