import sbt._
import sbt.Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName           = "coral"
  val appVersion        = "0.1.0-SNAPSHOT"
  val playVersion       = "2.1.1"
  val totalGridRelease  = "https://repo.totalgrid.org/artifactory/totalgrid-release"
  val totalGridSnapshot = "https://repo.totalgrid.org/artifactory/totalgrid-private-snapshot"
  val reefVersion       = "0.5.0-SNAPSHOT" // "0.4.8"

  lazy val baseSettings = Seq(
    version            := appVersion,
    // Need these scala versions or it tries Scala-2.9.2
    scalaVersion       := "2.10.0",
    //scalaBinaryVersion := "2.10",
    //crossScalaVersions := Seq("2.10.0"),
    organization       := "org.totalgrid.coral",
    scalacOptions += "-feature", // show compiler warnings for language features
    scalacOptions += "-unchecked", // compiler warnings for type aliases and ???
    resolvers += "scala-tools" at "http://repo.typesafe.com/typesafe/scala-tools-releases-cache",
    credentials += Credentials( Path.userHome / ".ivy2" / ".credentials"),
    resolvers += "totalgrid-snapshot" at totalGridSnapshot,
    resolvers += "totalgrid-release" at totalGridRelease//,
    //routesImport += "models.QueryBinders._"
  )

  lazy val core = Project("core", base = file("core"))
    .settings(baseSettings: _*)
    .settings(
      name := appName,
      libraryDependencies += "play" %% "play" % playVersion,
      libraryDependencies += "org.totalgrid.reef" % "reef-client" % reefVersion,
      libraryDependencies += "org.totalgrid.reef" % "reef-service-client" % reefVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5"
    )

  lazy val test = Project("test", base = file("test"))
    .settings(baseSettings: _*)
    .settings(
      name := appName + ".test",
      libraryDependencies += "play" %% "play-test" % playVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5"
    )
    .dependsOn(core)

  lazy val sample = play.Project("sample", path = file("sample"))
    .settings(baseSettings: _*)
    .settings(
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
      publishLocal := {},
      publish := {}
    )
    .dependsOn(core, test % "test")

  lazy val root = Project("root", base = file("."))
    .settings(baseSettings: _*)
    .settings(
      publishLocal := {},
      publish := {}
    )
    .aggregate(core, test, sample)

}
