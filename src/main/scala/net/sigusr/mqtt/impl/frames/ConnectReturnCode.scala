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

import scodec.Err

import scala.annotation.switch
import scalaz.\/

sealed trait ConnectReturnCode extends CaseEnum

case object ConnectionAccepted extends ConnectReturnCode { val enum = 0 }
case object ConnectionRefused1 extends ConnectReturnCode { val enum = 1 }
case object ConnectionRefused2 extends ConnectReturnCode { val enum = 2 }
case object ConnectionRefused3 extends ConnectReturnCode { val enum = 3 }
case object ConnectionRefused4 extends ConnectReturnCode { val enum = 4 }
case object ConnectionRefused5 extends ConnectReturnCode { val enum = 5 }

object ConnectReturnCode {

  import scala.language.implicitConversions

  implicit def fromEnum(enum: Int): \/[Err, ConnectReturnCode] =
    (enum: @switch) match {
      case 0 => \/.right(ConnectionAccepted)
      case 1 => \/.right(ConnectionRefused1)
      case 2 => \/.right(ConnectionRefused2)
      case 3 => \/.right(ConnectionRefused3)
      case 4 => \/.right(ConnectionRefused4)
      case 5 => \/.right(ConnectionRefused5)
      case _ => \/.left(Err("Connect return code encoded value should be in the range [0..5]"))
    }
}