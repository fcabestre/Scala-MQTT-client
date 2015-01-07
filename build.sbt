name := """Scala-MQTT-client"""

version := "1.0"

scalaVersion := "2.11.4"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "2.4.15" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.3.8",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.8",
  "org.typelevel" %% "scodec-core" % "1.6.0"
)

scalacOptions in Test ++= Seq("-Yrangepos")

scalacOptions ++= Seq("-language:implicitConversions", "-unchecked", "-feature", "-deprecation", "-Xfatal-warnings", "-Xlint:_")

net.virtualvoid.sbt.graph.Plugin.graphSettings

CoverallsPlugin.CoverallsKeys.coverallsToken := Some("4c62CwXnosg5iwouVOrCa46frQwqtBlzk")

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*examples.*"