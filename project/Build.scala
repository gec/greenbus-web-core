import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "reefgui"
    val appVersion      = "1.0-SNAPSHOT"


  val appDependencies = Seq(
    // Add your project dependencies here,
    "com.weiglewilczek.slf4s" % "slf4s_2.9.0-1" % "1.0.6",
    "org.totalgrid.reef" % "reef-client" % "0.4.8",
    "org.totalgrid.reef" % "reef-service-client" % "0.4.8"
  )


  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    // Add your own project settings here
   
    resolvers += "scala-tools" at "http://repo.typesafe.com/typesafe/scala-tools-releases-cache",
    resolvers += "totalgrid-release" at "https://repo.totalgrid.org/artifactory/totalgrid-release"
  )

}
