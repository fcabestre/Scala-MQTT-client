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

package net.sigusr.mqtt.impl.frames

import net.sigusr.mqtt.api._
import org.specs2.mutable._

object FrameSpec extends Specification {

  "Connect variable header" should {
    
    "Have a valid keep alive timer" in {
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = false, userNameFlag = true, keepAliveTimer = -1)  should throwA[IllegalArgumentException]
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = false, userNameFlag = true, keepAliveTimer = 65536)  should throwA[IllegalArgumentException]
    }
    "Have a valid combination of username and password flags" in {
      ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = true, userNameFlag = false, keepAliveTimer = 0)  should throwA[IllegalArgumentException]
    }
  }
}
