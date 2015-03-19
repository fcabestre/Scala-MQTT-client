import sbt._
import sbt.Keys._
import scoverage.ScoverageSbtPlugin
import org.scoverage.coveralls.CoverallsPlugin
import com.typesafe.sbt.pgp.PgpKeys._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._

object ScalaMQTTClientBuild extends Build {

  lazy val IntegrationTest = config("it") extend Test

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


  lazy val root = Project(
    id = "scala-mqtt-client",
    base = file("."),

    aggregate = Seq(core, examples),

    settings = commonSettings ++ scalariformSettings ++ Seq(
      publish := (),
      publishLocal := (),
      publishArtifact := false
    )
  )

  lazy val core = Project(
    id = "scala-mqtt-client-core",
    base = file("core"),

    configurations = Seq(IntegrationTest),

    settings = commonSettings ++ testSettings ++ pgpSetings ++ Publishing.settings ++ Seq(
      name := """Scala-MQTT-client""",
      version := "0.6.0-SNAPSHOT",

      resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2" % "2.4.15" % "test",
        "com.typesafe.akka" %% "akka-actor" % "2.3.9",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
        "org.typelevel" %% "scodec-core" % "1.6.0")
    )
  )

  lazy val examples = Project(
    id = "scala-mqtt-client-examples",
    base = file("examples"),

    dependencies = Seq(core),

    settings = commonSettings ++ Seq(
      publish := (),
      publishLocal := (),
      publishArtifact := false
    )
  )

  def commonSettings =
    Seq(
      organization := "net.sigusr",
      scalaVersion := "2.11.5",

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

  def scalariformSettings =
    defaultScalariformSettings ++ Seq(
      ScalariformKeys.preferences :=
        ScalariformKeys.preferences.value.setPreference(RewriteArrowSymbols, true)
    )
}

