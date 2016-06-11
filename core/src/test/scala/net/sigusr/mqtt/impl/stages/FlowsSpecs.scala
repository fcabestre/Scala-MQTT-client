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

package net.sigusr.mqtt.impl.stages

import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import net.sigusr.mqtt.SpecsTestKit
import net.sigusr.mqtt.api.{AtLeastOnce, AtMostOnce}
import net.sigusr.mqtt.impl.frames.{ConnackFrame, Frame, Header, PublishFrame}
import org.specs2.mutable._
import scodec.Codec
import scodec.bits.ByteVector

object FlowsSpecs extends Specification {

  "A bytes to frames flow" should {

    "Provide a frame from a correct byte stream" in new SpecsTestKit {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connackFrame = ConnackFrame(header, 0)
      val encodedConnackFrame = ByteString(Codec[Frame].encode(connackFrame).require.toByteArray)

      val flow = new BytesToFrames
      val (pub, sub) = TestSource.probe[ByteString].via(flow).toMat(TestSink.probe[Frame])(Keep.both).run()

      sub.request(1)
      pub.sendNext(encodedConnackFrame)
      val result = sub.expectNext()
      result should_=== connackFrame
    }

    "Provide a frame from a (big) correct byte stream" in new SpecsTestKit {
      import net.sigusr.mqtt.SpecUtils._

      val bigPayload = makeRandomByteVector(2500)
      val bigFrame = PublishFrame(Header(qos = AtLeastOnce.enum), "topic", 42, ByteVector(bigPayload))
      val encodedBigFrame = ByteString(Codec[Frame].encode(bigFrame).require.toByteArray)
      val encodedBigFramePart1 = encodedBigFrame.take(1500)
      val encodedBigFramePart2 = encodedBigFrame.drop(1500)

      val flow = new BytesToFrames
      val (pub, sub) = TestSource.probe[ByteString].via(flow).toMat(TestSink.probe[Frame])(Keep.both).run()

      sub.request(1)
      pub.sendNext(encodedBigFramePart1)
      pub.sendNext(encodedBigFramePart2)
      val result = sub.expectNext()
      result should_=== bigFrame
    }

    "Provide two frames from a (big) correct byte stream" in new SpecsTestKit {
      val frame0x01 = PublishFrame(Header(qos = AtLeastOnce.enum), "topic", 0, ByteVector(0x01))
      val frame0x7f = PublishFrame(Header(qos = AtLeastOnce.enum), "topic", 0, ByteVector(0x7f))
      val encodedFrame0x01 = ByteString(Codec[Frame].encode(frame0x01).require.toByteArray)
      val encodedFrame0x7f = ByteString(Codec[Frame].encode(frame0x7f).require.toByteArray)

      val flow = new BytesToFrames
      val (pub, sub) = TestSource.probe[ByteString].via(flow).toMat(TestSink.probe[Frame])(Keep.both).run()

      sub.request(2)
      pub.sendNext(encodedFrame0x01 ++ encodedFrame0x7f)
      sub.expectNextN(List(frame0x01, frame0x7f))
    }

    "Fail from a failing byte stream" in new SpecsTestKit {
      val garbageFrame = ByteString(0xff)

      val flow = new BytesToFrames
      val (pub, sub) = TestSource.probe[ByteString].via(flow).toMat(TestSink.probe[Frame])(Keep.both).run()

      sub.request(1)
      pub.sendNext(garbageFrame)
      val result = sub.expectError()
    }
  }
}
