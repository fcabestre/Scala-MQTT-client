name := """hello-akka"""

version := "1.0"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.1",
  "org.typelevel" %% "scodec-core" % "1.0.0",
  "org.specs2" %% "specs2" % "2.3.12" % "test"
)

//  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
//  "junit" % "junit" % "4.11" % "test",
//  "com.novocode" % "junit-interface" % "0.10" % "test"

//testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

scalacOptions in Test ++= Seq("-Yrangepos")

