name := """Scala-MQTT-client"""

version := "1.0"

scalaVersion := "2.11.2"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "2.4.4" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6",
  "org.typelevel" %% "scodec-core" % "1.3.1"
)

scalacOptions in Test ++= Seq("-Yrangepos")

scalacOptions ++= Seq("-feature", "-deprecation")

net.virtualvoid.sbt.graph.Plugin.graphSettings

scoverage.ScoverageSbtPlugin.instrumentSettings

org.scoverage.coveralls.CoverallsPlugin.coverallsSettings

org.scoverage.coveralls.CoverallsPlugin.CoverallsKeys.coverallsToken := Some("4c62CwXnosg5iwouVOrCa46frQwqtBlzk")