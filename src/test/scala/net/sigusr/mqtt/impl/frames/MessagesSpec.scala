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

import net.sigusr.mqtt.api.MQTTMessageId
import net.sigusr.mqtt.api._
import org.specs2.mutable._
import scodec.Err

object MessagesSpec extends Specification {

  "Quality of service" should {
    "Provide their «enum» value" in {
      AtMostOnce.enum should be_==(0)
      AtLeastOnce.enum should be_==(1)
      ExactlyOnce.enum should be_==(2)
    }

    "Be constructable from their corresponding «enum» value" in {
      import net.sigusr.mqtt.SpecUtils._
      import net.sigusr.mqtt.impl.frames.QualityOfService._

      fromEnum(-1) should failWith(Err("Quality of service encoded value should be in the range [0..2]"))
      fromEnum(0) should succeedWith(AtMostOnce)
      fromEnum(1) should succeedWith(AtLeastOnce)
      fromEnum(2) should succeedWith(ExactlyOnce)
      fromEnum(3) should failWith(Err("Quality of service encoded value should be in the range [0..2]"))
    }
  }

  "Connect variable header" should {
    "Have a valid keep alive timer" in {
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce, willRetain = false, passwordFlag = false, userNameFlag = true, keepAliveTimer = -1)  should throwA[IllegalArgumentException]
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce, willRetain = false, passwordFlag = false, userNameFlag = true, keepAliveTimer = 65536)  should throwA[IllegalArgumentException]
    }
    "Have a valid combination of username and password flags" in {
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce, willRetain = false, passwordFlag = true, userNameFlag = false, keepAliveTimer = 0)  should throwA[IllegalArgumentException]
    }
  }

  "Connack return code" should {
    "Provide their «enum» value" in {
      ConnectionAccepted.enum should be_==(0)
      ConnectionRefused2.enum should be_==(2)
      ConnectionRefused4.enum should be_==(4)
      ConnectionRefused5.enum should be_==(5)
    }

    "Be constructable from their corresponding «enum» value" in {
      import net.sigusr.mqtt.SpecUtils._
      import net.sigusr.mqtt.impl.frames.ConnectReturnCode._

      fromEnum(-1) should failWith(Err("Connect return code encoded value should be in the range [0..5]"))
      fromEnum(0) should succeedWith(ConnectionAccepted)
      fromEnum(1) should succeedWith(ConnectionRefused1)
      fromEnum(2) should succeedWith(ConnectionRefused2)
      fromEnum(3) should succeedWith(ConnectionRefused3)
      fromEnum(4) should succeedWith(ConnectionRefused4)
      fromEnum(5) should succeedWith(ConnectionRefused5)
      fromEnum(6) should failWith(Err("Connect return code encoded value should be in the range [0..5]"))
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
