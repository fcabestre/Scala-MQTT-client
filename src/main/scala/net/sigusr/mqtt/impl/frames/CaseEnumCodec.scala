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

import scodec.bits.BitVector
import scodec.codecs._
import scodec.{Codec, Err}

import scalaz.\/

class CaseEnumCodec[T <: CaseEnum](codec: Codec[Int])(implicit fromEnum: Function[Int, \/[Err, T]]) extends Codec[T] {

   override def decode(bits: BitVector): \/[Err, (BitVector, T)] =
     codec.decode(bits) flatMap {
       (b: BitVector, i: Int) => fromEnum(i) flatMap {
         (m: T) => \/.right((b, m))
       }
     }

   override def encode(value: T): \/[Err, BitVector] = codec.encode(value.enum)
 }
