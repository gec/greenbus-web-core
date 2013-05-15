import sbt._
import sbt.Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "coral"
    val appVersion      = "0.1.0"
    val totalGridRelease = "https://repo.totalgrid.org/artifactory/totalgrid-release"
    val totalGridSnapshot    = "https://repo.totalgrid.org/artifactory/totalgrid-private-snapshot"
    val totalGridVersion = "0.5.0-SNAPSHOT" // "0.4.8"

  val appDependencies = Seq(
    // Add your project dependencies here,
    "com.weiglewilczek.slf4s" % "slf4s_2.9.1" % "1.0.7",
    "org.totalgrid.reef" % "reef-client" % totalGridVersion,
    "org.totalgrid.reef" % "reef-service-client" % totalGridVersion
  )

  val SCALA_VERSION = "2.10.0" // "2.9.0"
  lazy val baseSettings = Seq(
    version            := appVersion,
    scalaVersion       := SCALA_VERSION,
    //scalaBinaryVersion := "2.10",
    crossScalaVersions := Seq(SCALA_VERSION),
    organization       := "org.totalgrid",
    scalacOptions += "-feature",
    resolvers += "scala-tools" at "http://repo.typesafe.com/typesafe/scala-tools-releases-cache",
    credentials += Credentials( Path.userHome / ".ivy2" / ".credentials"),
    resolvers += "totalgrid-snapshot" at totalGridSnapshot,
    resolvers += "totalgrid-release" at totalGridRelease,
    routesImport += "models.QueryBinders._"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    scalacOptions += "-feature",
    resolvers += "scala-tools" at "http://repo.typesafe.com/typesafe/scala-tools-releases-cache",
    credentials += Credentials( Path.userHome / ".ivy2" / ".credentials"),
    resolvers += "totalgrid-snapshot" at totalGridSnapshot,
    resolvers += "totalgrid-release" at totalGridRelease,
    routesImport += "models.QueryBinders._"
  )

  /*
  lazy val sample = play.Project("sample", path = file("sample"))
    .settings(baseSettings: _*)
    .dependsOn(main)
    //.dependsOn(main, test % "test")
  */

}
