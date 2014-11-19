# A Scala MQTT client library [![Build Status](https://travis-ci.org/fcabestre/Scala-MQTT-client.svg?branch=master)](https://travis-ci.org/fcabestre/Scala-MQTT-client) [![Coverage Status](https://coveralls.io/repos/fcabestre/Scala-MQTT-client/badge.png?branch=master)](https://coveralls.io/r/fcabestre/Scala-MQTT-client?branch=master)

## Introduction

The initial purpose of this projects is twofold:

1. To provide an MQTT client library purely written in Scala with Akka. Particularly, I did not wanted to build it around an existing Java MQTT library.
2. To learn how to implement a network protocol with [Akka.io](http://doc.akka.io/docs/akka/snapshot/scala/io.html) and fully benefit from Akka's asynchronous programming model.

But quickly, when I came to look at how to encode/decode MQTT protocol frames, I stumbled upon [Scodec](http://typelevel.org/projects/scodec). This seemed to be the encoding/decoding framework I was waiting for a long time. So I decided to give it a try...

## Dependencies

* Scala 2.11.2
* Akka 2.3.6
* scodec-core 1.3.1
