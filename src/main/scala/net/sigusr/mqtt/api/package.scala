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

package net.sigusr.mqtt

package object api {

  val DEFAULT_KEEP_ALIVE: Int = 30

  implicit def asMessageIdentifier(int: Int): MessageId = MessageId(int)

  implicit class MessageIdentifierLiteral(val sc: StringContext) extends AnyVal {
    def mi(args: Any*): MessageId = {
      val strings = sc.parts.iterator
      val expressions = args.iterator
      val buf = new StringBuffer(strings.next())
      while (strings.hasNext) {
        buf append expressions.next
        buf append strings.next
      }
      MessageId(buf.toString.toInt)
    }
  }

  @inline final def assert(requirement: Boolean, message: ⇒ Any): Unit = {
    if (!requirement)
      throw new IllegalArgumentException(message.toString)
  }
}
