import sbt._
import sbt.Keys._
import play.Project._
import com.google.javascript.jscomp.{CompilerOptions, CompilationLevel}

// See example at: https://github.com/t2v/play2-auth
//

object ApplicationBuild extends Build {

  val appName           = "coral"
  val playVersion       = "2.2.1"
  val totalGridRelease  = "https://repo.totalgrid.org/artifactory/totalgrid-release"
  val totalGridSnapshot = "https://repo.totalgrid.org/artifactory/totalgrid-private-snapshot"
  val reefVersion       = "0.6.0.M3-SNAPSHOT"
  val msgVersion       = "0.0.1-SNAPSHOT"

  lazy val baseSettings = Seq(
    version            := "0.2.0-SNAPSHOT",
    // Need these scala versions or it tries the wrong version
    scalaVersion       := "2.10.3",
    //scalaBinaryVersion := "2.10",
    //crossScalaVersions := Seq("2.10.3"),
    organization       := "org.totalgrid.coral",
    scalacOptions += "-feature", // show compiler warnings for language features
    scalacOptions += "-unchecked", // compiler warnings for type aliases and ???
    resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "scala-tools" at "http://repo.typesafe.com/typesafe/scala-tools-releases-cache",
    credentials += Credentials( Path.userHome / ".ivy2" / ".credentials"),
    resolvers += "totalgrid-snapshot" at totalGridSnapshot,
    resolvers += "totalgrid-release" at totalGridRelease//,
    //routesImport += "models.QueryBinders._"
  )

  lazy val appPublishMavenStyle = true
  lazy val appPublishArtifactInTest = false
  lazy val appPomIncludeRepository = { _: MavenRepository => false }
  lazy val appPublishTo = { (v: String) =>
    val artifactory = "https://repo.totalgrid.org/artifactory/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at artifactory + "totalgrid-private-snapshot")
    else
      Some("releases"  at artifactory + "totalgrid-private-release")
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

//  val defaultOptions = new CompilerOptions()
//  CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(defaultOptions)
//  defaultOptions.setProcessCommonJSModules(false)
//  defaultOptions.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5)

//  val sampleJavascriptOptions = new CompilerOptions()
//  sampleJavascriptOptions.closurePass = true
//  sampleJavascriptOptions.setProcessCommonJSModules( true)
//  sampleJavascriptOptions.setCommonJSModulePathPrefix( baseDirectory + "/sample/app/assets/javascripts/")
//  sampleJavascriptOptions.setLanguageIn( CompilerOptions.LanguageMode.ECMASCRIPT5)
//  CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel( sampleJavascriptOptions)

  lazy val core = Project("core", base = file("core"))
    .settings(baseSettings: _*)
    .settings(
      name := appName,
      libraryDependencies += "com.typesafe.play"  %% "play" % playVersion % "provided",
      libraryDependencies += "com.typesafe.play" %% "play-test" % playVersion % "test",
      libraryDependencies += "org.totalgrid.reef" % "reef-client" % reefVersion withSources(),
      libraryDependencies += "org.totalgrid.msg" % "msg-qpid" % msgVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5" % "test",
      libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % playVersion % "test",
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
      name := appName + ".test",
      libraryDependencies += "com.typesafe.play" %% "play-test" % playVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5",
      publishMavenStyle       := appPublishMavenStyle,
      publishArtifact in Test := appPublishArtifactInTest,
      pomIncludeRepository    := appPomIncludeRepository,
      publishTo               <<=(version)(appPublishTo),
      pomExtra                := appPomExtra
    )
    .dependsOn(core)

  lazy val sample = play.Project("sample", path = file("sample"))
    .settings(baseSettings: _*)
    .settings(
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
      publishLocal := {},
      publish := {}
    )
    .settings(
      requireJs += "login.js",
      requireJs += "app.js",
      requireJsShim += "app.js"
    )
//  .settings(
//    (Seq(requireJs += "login.js", requireJsShim += "login.js") ++ closureCompilerSettings(sampleJavascriptOptions)): _*
    //closureCompilerSettings(sampleJavascriptOptions) ++
    //Seq(javascriptEntryPoints <<= baseDirectory(_ / "sample" / "app" / "assets" / "js" ** "app.js")) : _*
//  )
    .dependsOn(core, test % "test")

  lazy val root = Project("root", base = file("."))
    .settings(baseSettings: _*)
    .settings(
      publishLocal := {},
      publish := {}
    )
    .aggregate(core, test, sample)

}
