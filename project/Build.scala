import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "reefgui"
    val appVersion      = "1.0-SNAPSHOT"


  val appDependencies = Seq(
    // Add your project dependencies here,
    "org.totalgrid.reef" % "reef-client" % "0.4.8",
    "org.totalgrid.reef" % "reef-service-client" % "0.4.8"
  )


  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    // Add your own project settings here

    resolvers += "totalgrid-release" at "https://repo.totalgrid.org/artifactory/totalgrid-release"
  )

}
