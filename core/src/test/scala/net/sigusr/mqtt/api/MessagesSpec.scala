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

package net.sigusr.mqtt.api

import net.sigusr.mqtt.api.ConnectionFailureReason.{ BadProtocolVersion, BadUserNameOrPassword, IdentifierRejected, NotAuthorized, ServerNotResponding, ServerUnavailable }
import net.sigusr.mqtt.api.QualityOfService.{ AtLeastOnce, AtMostOnce, ExactlyOnce }
import org.specs2.mutable._

object MessagesSpec extends Specification {

  "Quality of service" should {
    "Provide their «enum» value" in {
      AtMostOnce.value should be_==(0)
      AtLeastOnce.value should be_==(1)
      ExactlyOnce.value should be_==(2)
    }

    "Be constructable from their corresponding «enum» value" in {
      import net.sigusr.mqtt.api.QualityOfService._

      withValue(-1) should throwA[IllegalArgumentException]
      withValue(0) should_=== AtMostOnce
      withValue(1) should_=== AtLeastOnce
      withValue(2) should_=== ExactlyOnce
      withValue(3) should throwA[IllegalArgumentException]
    }
  }

  "Connect failure reason" should {
    "Provide their «enum» value" in {
      ServerNotResponding.value should be_==(0)
      BadProtocolVersion.value should be_==(1)
      IdentifierRejected.value should be_==(2)
      ServerUnavailable.value should be_==(3)
      BadUserNameOrPassword.value should be_==(4)
      NotAuthorized.value should be_==(5)
    }

    "Be constructable from their corresponding «enum» value" in {
      import net.sigusr.mqtt.api.ConnectionFailureReason._

      withValue(-1) should throwA[IllegalArgumentException]
      withValue(0) should_=== ServerNotResponding
      withValue(1) should_=== BadProtocolVersion
      withValue(2) should_=== IdentifierRejected
      withValue(3) should_=== ServerUnavailable
      withValue(4) should_=== BadUserNameOrPassword
      withValue(5) should_=== NotAuthorized
      withValue(6) should throwA[IllegalArgumentException]
    }
  }

  "MessageIdentifier" should {
    "Check the range of the integer provided to its constructor" in {
      MessageId(-1) should throwA[IllegalArgumentException]
      MessageId(0) should not(throwA[IllegalArgumentException])
      MessageId(65535) should not(throwA[IllegalArgumentException])
      MessageId(65536) should throwA[IllegalArgumentException]
    }

    "Allow pattern matching" in {
      MessageId(42) match {
        case MessageId(i) => i should_=== 42
      }
    }

    "Have a literal syntax" in {
      val four = 4
      val two = 2
      mi"$four$two" should_=== MessageId(42)
      mi"42" should_=== MessageId(42)
      mi"-1" should throwA[IllegalArgumentException]
      mi"65536" should throwA[IllegalArgumentException]
      mi"fortytwo" should throwA[NumberFormatException]
    }

    "Have a implicit conversion from Int" in {
      def id(messageIdentifier: MessageId): MessageId = messageIdentifier
      id(42) should_=== MessageId(42)
      id(-1) should throwA[IllegalArgumentException]
      id(65536) should throwA[IllegalArgumentException]
    }
  }

  "Connect" should {
    "Check the range of the keep alive value provided to its constructor" in {
      Connect("Client", keepAlive = -1) should throwA[IllegalArgumentException]
      Connect("Client", keepAlive = 0) should not(throwA[IllegalArgumentException])
      Connect("Client", keepAlive = 65635) should not(throwA[IllegalArgumentException])
      Connect("Client", keepAlive = 65636) should throwA[IllegalArgumentException]
    }

    "Check a user is provided when a password is set" in {
      Connect("Client", password = Some("pass")) should throwA[IllegalArgumentException]
      Connect("Client", user = Some("user"), password = Some("pass")) should not(throwA[IllegalArgumentException])

    }
  }

  "Publish" should {
    "Have a valid combination of QoS and message identifier" in {
      Publish("topic", Vector(0x00), AtMostOnce) should not(throwA[IllegalArgumentException])
      Publish("topic", Vector(0x00), AtLeastOnce) should throwA[IllegalArgumentException]
      Publish("topic", Vector(0x00), AtLeastOnce, Some(1)) should not(throwA[IllegalArgumentException])
      Publish("topic", Vector(0x00), ExactlyOnce) should throwA[IllegalArgumentException]
      Publish("topic", Vector(0x00), ExactlyOnce, Some(1)) should not(throwA[IllegalArgumentException])
    }
  }
}
