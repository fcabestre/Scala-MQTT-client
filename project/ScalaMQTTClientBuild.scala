import org.scoverage.coveralls.CoverallsPlugin
import sbt.Keys._
import sbt._
import scoverage.ScoverageSbtPlugin
import com.typesafe.sbt.pgp.PgpKeys._

object ScalaMQTTClientBuild extends Build {
  lazy val IntegrationTest = config("it") extend Test

  def itFilter(name: String): Boolean = name startsWith "net.sigusr.mqtt.integration"
  def unitFilter(name: String): Boolean = !itFilter(name)

  lazy val root = (project in file(".")).
    configs(IntegrationTest).
    settings(inConfig(IntegrationTest)(Defaults.testTasks): _*).
    settings(
      name := """Scala-MQTT-client""",
      version := "0.6.0-SNAPSHOT",
      organization := "net.sigusr",
      scalaVersion := "2.11.5",
      resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2" % "2.4.15" % "test",
        "com.typesafe.akka" %% "akka-actor" % "2.3.9",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
        "org.typelevel" %% "scodec-core" % "1.6.0"),
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
        "-Ywarn-unused-import"),
      useGpg := true,
      gpgCommand := "/usr/bin/gpg2",
      pgpSecretRing := file("~/.gnupg/secring.gpg"),
      testOptions in Test := Seq(Tests.Filter(unitFilter)),
      testOptions in IntegrationTest := Seq(Tests.Filter(itFilter))).
      settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*).
      settings(ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*examples.*").
      settings(Publishing.settings: _*).
      settings(mappings in (Compile, packageBin) ~= { _.filter(!_._2.matches(".*/examples/.*")) })
}

