# A Scala MQTT client library [![Build Status](https://travis-ci.org/fcabestre/Scala-MQTT-client.svg?branch=master)](https://travis-ci.org/fcabestre/Scala-MQTT-client) [![Coverage Status](https://coveralls.io/repos/fcabestre/Scala-MQTT-client/badge.png?branch=master)](https://coveralls.io/r/fcabestre/Scala-MQTT-client?branch=master)

## In the beginning...

I wanted to build a presentation of [Akka](http://akka.io). But not just a deck of slides, I wanted something to
live-code with, something backed by real hardware. Maybe a Raspberry Pi. I imagined the small devices sending
messages to an MQTT broker, probably [Mosquitto](http://mosquitto.org).

To this purpose, I looked for an MQTT library which I could use with Scala, but those I found were only Java based.
I thought: "Could be fun to implement the MQTT protocol directly with [Akka IO](http://doc.akka.io/docs/akka/snapshot/scala/io.html).
Its [specification](http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html) is rather short
(around 42 printed pages)".

And quickly, when I came to look at how to encode/decode MQTT protocol frames, I stumbled upon
[Scodec](http://typelevel.org/projects/scodec). This seemed to be the encoding/decoding framework I was waiting for
a long time. So I decided to give it a try...

## And now

I have a basic and far from complete implementation of the thing. Frame encoding and decoding works pretty well, and
it's possible to write some code to talk to [Mosquitto](http://mosquitto.org). For examples you can have a look to the [local subscriber]
(https://github.com/fcabestre/Scala-MQTT-client/blob/master/examples/src/main/scala/net/sigusr/mqtt/examples/LocalSubscriber.scala) or the
[local publisher](https://github.com/fcabestre/Scala-MQTT-client/blob/master/examples/src/main/scala/net/sigusr/mqtt/examples/LocalPublisher.scala).
I'm starting to take it a bit more seriously. I mean, thinking of doing something that could be useful to others. But
there is still a lot of work to be done:

  * Learning how other MQTT APIs are organised to polish this one
  * Managing communication back pressure with [Akka IO](http://doc.akka.io/docs/akka/snapshot/scala/io.html)
  * Suppoting both MQTT v3.1 and v3.1.1
  * If I dare, passing [Paho](http://www.eclipse.org/paho/clients/testing/) conformance tests
  * And many, many, many more I can't foresee...

## First release ever...

[ci]: https://travis-ci.org/fcabestre/Scala-MQTT-client/
[sonatype]: https://oss.sonatype.org/index.html#nexus-search;quick~scala-mqtt-client

Artifacts are available at [Sonatype OSS Repository Hosting service][sonatype], even the ```SNAPSHOTS``` automatically
built by [Travis CI][ci]. To include the Sonatype repositories in your SBT build you should add,

```scala
resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
)
```

In case you want to easily give a try to this library, without the burden of adding resolvers, there is a release synced
to Maven Central. In this case just add,

```scala
scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
    "net.sigusr" %% "scala-mqtt-client" % "0.5.0"
)
```

## Dependencies

  * Scala 2.11.6
  * Akka 2.3.9
  * scodec-core 1.7.1
  * Scalaz 7.1.1

## License

This work is licenced under an [Apache Version 2.0 license](http://github.com/fcabestre/Scala-MQTT-client/blob/master/LICENSE)
