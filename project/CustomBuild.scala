import sbt._
import Keys._

object CustomBuild extends Build
{
  lazy val specs2 = "org.specs2" %% "specs2" % "2.4.15"
  lazy val IntegrationTest = config("it") extend Test

  def itFilter(name: String): Boolean = name startsWith  "net.sigusr.mqtt.integration"
  def unitFilter(name: String): Boolean = !itFilter(name)

  lazy val root = (project in file(".")).
    configs(IntegrationTest).
    settings(inConfig(IntegrationTest)(Defaults.testTasks): _*).
    settings(
      libraryDependencies += specs2 % IntegrationTest,
      testOptions in Test := Seq(Tests.Filter(unitFilter)),
      testOptions in IntegrationTest := Seq(Tests.Filter(itFilter))
    )
}

