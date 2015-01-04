# A Scala MQTT client library [![Build Status](https://travis-ci.org/fcabestre/Scala-MQTT-client.svg?branch=master)](https://travis-ci.org/fcabestre/Scala-MQTT-client) [![Coverage Status](https://coveralls.io/repos/fcabestre/Scala-MQTT-client/badge.png?branch=master)](https://coveralls.io/r/fcabestre/Scala-MQTT-client?branch=master)

## In the beginning...

I wanted to build a presentation of [Akka](http://akka.io). But not just a deck of slides, I wanted something to
live-code with, something backed by real hardware. Maybe a Raspberry Pi. I imagined the small devices sending
messages to an MQTT broker, probably [Mosquitto](http://mosquitto.org).

To this purpose, I looked for an MQTT library which I could use with Scala, but those I found were only Java based.
I thought: "Could be fun to implement the MQTT protocol directly with [Akka IO](http://doc.akka.io/docs/akka/snapshot/scala/io.html).
Its [specification](http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html) is rather short
(around 42 printed pages)".

But quickly, when I came to look at how to encode/decode MQTT protocol frames, I stumbled upon
[Scodec](http://typelevel.org/projects/scodec). This seemed to be the encoding/decoding framework I was waiting for
a long time. So I decided to give it a try...

## And now

I have a basic and far from complete implementation of the thing. Frame encoding and decoding works pretty well, and
it's possible to write some code to talk to [Mosquitto](http://mosquitto.org). See for example this [local subscriber]
(https://github.com/fcabestre/Scala-MQTT-client/blob/master/src/main/scala/net/sigusr/examples/LocalSubscriber.scala)

I'm starting to take it a bit more seriously. I mean, thinking of doing something that could be useful to others. But
there is still a lot of work to be done:

  * Learning how other MQTT APIs are organised to polish this one
  * Properly handling quality of services of 1 and 2 with timeouts and
    error messages
  * Managing communication back pressure with [Akka IO](http://doc.akka.io/docs/akka/snapshot/scala/io.html)
  * If I dare, passing [Paho](http://www.eclipse.org/paho/clients/testing/) conformance tests
  * And many, many, many more I can't foresee...

## Dependencies

  * Scala 2.11.4
  * Akka 2.3.8
  * scodec-core 1.6.0

## License

This work is licenced under an [Apache Version 2.0 license](http://github.com/fcabestre/Scala-MQTT-client/blob/master/LICENSE)
