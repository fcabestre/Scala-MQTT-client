import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbt.pgp.PgpKeys._
import sbt.Keys._
import sbt._

import scalariform.formatter.preferences._

object ScalaMQTTClientBuild extends Build {

  lazy val IntegrationTest = config("it") extend Test
  
  lazy val core = Project(
    id = "core",
    base = file("core"),

    configurations = Seq(IntegrationTest),

    settings = commonSettings ++ scalariformSettings ++ testSettings ++ pgpSetings ++ Publishing.settings ++ Seq(
      name := """Scala-MQTT-client""",
      version := "0.7.0-SNAPSHOT",

      resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",

      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2-core" % "3.8.6" % "test",
        "com.typesafe.akka" %% "akka-actor" % "2.4.14",
        "com.typesafe.akka" %% "akka-testkit" % "2.4.14",
        "com.typesafe.akka" %% "akka-stream" % "2.4.14",
        "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.14",
        "org.scodec" %% "scodec-core" % "1.10.3",
        "org.scalaz" %% "scalaz-core" % "7.2.8")
    )
  )

  lazy val examples = Project(
    id = "examples",
    base = file("examples"),

    dependencies = Seq(core),

    settings = commonSettings ++ scalariformSettings ++ Seq(
      publish := (),
      publishLocal := (),
      publishArtifact := false
    )
  )

  def commonSettings =
    Seq(
      organization := "net.sigusr",
      scalaVersion := "2.12.1",

      scalacOptions in Test ++= Seq("-Yrangepos"),

      scalacOptions ++= Seq(
        "-language:implicitConversions",
        "-unchecked",
        "-feature",
        "-deprecation",
        "-encoding", "UTF-8",
        "-language:existentials",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Xfatal-warnings",
        "-Xlint:_",
        "-Xfuture",
        "-Yno-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ywarn-value-discard",
        "-Ywarn-unused-import")
    )

  def itFilter(name: String): Boolean = name startsWith "net.sigusr.mqtt.integration"
  def unitFilter(name: String): Boolean = !itFilter(name)

  def testSettings =
    Seq(
      testOptions in Test := Seq(Tests.Filter(unitFilter)),
      testOptions in IntegrationTest := Seq(Tests.Filter(itFilter))
    ) ++ inConfig(IntegrationTest)(Defaults.testTasks)

  def pgpSetings =
    Seq(
      useGpg := true,
      gpgCommand := "/usr/bin/gpg2",
      pgpSecretRing := file("~/.gnupg/secring.gpg")
    )


  def scalariformSettings =
    defaultScalariformSettings ++ Seq(
      ScalariformKeys.preferences :=
        ScalariformKeys.preferences.value.setPreference(RewriteArrowSymbols, true)
    )
}

