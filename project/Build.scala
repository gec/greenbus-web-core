import sbt._
import sbt.Keys._
import play.twirl.sbt.Import.TwirlKeys
import com.google.javascript.jscomp.{CompilerOptions, CompilationLevel}

// See example at: https://github.com/t2v/play2-auth
//

object ApplicationBuild extends Build {
  import play.Play.autoImport._
  import PlayKeys._

  val appName           = "web-core"
  val playVersion       = "2.3.6"
  val totalGridRelease  = "https://repo.totalgrid.org/artifactory/totalgrid-release"
  val totalGridSnapshot = "https://repo.totalgrid.org/artifactory/totalgrid-private-snapshot"
  val reefVersion       = "0.6.0.M4-SNAPSHOT"
  val msgVersion       = "0.0.1-SNAPSHOT"

  lazy val baseSettings = Seq(
    version            := "0.3.0-SNAPSHOT",
    // Need these scala versions or it tries the wrong version
    scalaVersion       := "2.10.4",
    organization       := "io.greenbus.web",
    scalacOptions += "-feature", // show compiler warnings for language features
    scalacOptions += "-unchecked", // show compiler warnings for type aliases and ???
    scalacOptions += "-deprecation", // show compiler warnings for deprecated features
    resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "scala-tools" at "http://repo.typesafe.com/typesafe/scala-tools-releases-cache",
    credentials += Credentials( Path.userHome / ".ivy2" / ".credentials"),
    resolvers += "totalgrid-snapshot" at totalGridSnapshot,
    resolvers += "totalgrid-release" at totalGridRelease
  )

  lazy val appPublishMavenStyle = true
  lazy val appPublishArtifactInTest = false
  lazy val appPomIncludeRepository = { _: MavenRepository => false }
  lazy val appPublishTo = { (v: String) =>
    val artifactory = "https://repo.totalgrid.org/artifactory/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at artifactory + "totalgrid-snapshot")
    else
      Some("releases"  at artifactory + "totalgrid-release")
  }
  lazy val appPomExtra = {
    <url>https://github.com/gec/coral.git</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:gec/coral.git</url>
        <connection>scm:git:git@github.com:gec/coral.git</connection>
      </scm>
  }


  lazy val core = Project("core", base = file("core"))
    .settings(baseSettings: _*)
    .settings(
      name := appName,
      libraryDependencies += "com.typesafe.play"  %% "play" % playVersion % "provided",
      libraryDependencies += "com.typesafe.play" %% "play-test" % playVersion % "test",
      libraryDependencies += play.PlayImport.cache,
      libraryDependencies += "org.totalgrid.reef" % "reef-client" % reefVersion withSources(),
      libraryDependencies += "org.totalgrid.msg" % "msg-qpid" % msgVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5" % "test",
      libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % playVersion % "test",
      libraryDependencies += "com.typesafe.slick" %% "slick" % "2.1.0",
      libraryDependencies += "com.typesafe.play" %% "play-slick" % "0.8.1",
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
      publishMavenStyle       := appPublishMavenStyle,
      publishArtifact in Test := appPublishArtifactInTest,
      pomIncludeRepository    := appPomIncludeRepository,
      publishTo               <<=(version)(appPublishTo),
      pomExtra                := appPomExtra
    )

  lazy val test = Project("test", base = file("test"))
    .settings(baseSettings: _*)
    .settings(
      name := appName + "-test",
      libraryDependencies += "com.typesafe.play" %% "play-test" % playVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5",
      libraryDependencies += "com.typesafe.slick" %% "slick" % "2.1.0",
      libraryDependencies += "com.typesafe.play" %% "play-slick" % "0.8.1",
      publishMavenStyle       := appPublishMavenStyle,
      publishArtifact in Test := appPublishArtifactInTest,
      pomIncludeRepository    := appPomIncludeRepository,
      publishTo               <<=(version)(appPublishTo),
      pomExtra                := appPomExtra
    )
    .dependsOn(core)

  lazy val sample = Project("sample", base = file("sample"))
    .enablePlugins(play.PlayScala)
    .settings(baseSettings: _*)
    .settings(
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
      TwirlKeys.templateImports in Compile += "io.greenbus.web._",
      publishLocal := {},
      publish := {}
    )
    .settings(
      requireJs += "login.js",
      requireJs += "app.js",
      requireJsShim += "app.js"
    )
    .dependsOn(core, test % "test")

  lazy val root = Project("root", base = file("."))
    .enablePlugins(play.PlayScala)
    .settings(baseSettings: _*)
    .settings(
      publishLocal := {},
      publish := {}
    )
    .aggregate(core, test, sample)

}
