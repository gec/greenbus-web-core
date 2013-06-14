import sbt._
import sbt.Keys._
import play.Project._
import com.google.javascript.jscomp.{CompilerOptions, CompilationLevel}

object ApplicationBuild extends Build {

  val appName           = "coral"
  val playVersion       = "2.1.1"
  val totalGridRelease  = "https://repo.totalgrid.org/artifactory/totalgrid-release"
  val totalGridSnapshot = "https://repo.totalgrid.org/artifactory/totalgrid-private-snapshot"
  val reefVersion       = "0.5.0-SNAPSHOT" // "0.4.8"

  lazy val baseSettings = Seq(
    version            := "0.1.0-SNAPSHOT",
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

  lazy val appPublishMavenStyle = true
  lazy val appPublishArtifactInTest = false
  lazy val appPomIncludeRepository = { _: MavenRepository => false }
  lazy val appPublishTo = { (v: String) =>
    val nexus = "https://repo.totalgrid.org/artifactory/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "totalgrid-private-snapshot")
    else
      Some("releases"  at nexus + "totalgrid-private-release")
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
      libraryDependencies += "play" %% "play" % playVersion,
      libraryDependencies += "org.totalgrid.reef" % "reef-client" % reefVersion,
      libraryDependencies += "org.totalgrid.reef" % "reef-service-client" % reefVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5",
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
      libraryDependencies += "play" %% "play-test" % playVersion,
      libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5",
      publishMavenStyle       := appPublishMavenStyle,
      publishArtifact in Test := appPublishArtifactInTest,
      pomIncludeRepository    := appPomIncludeRepository,
      publishTo               <<=(version)(appPublishTo),
      pomExtra                := appPomExtra
    )
    .settings(
      requireJs += "appLogin.js"
    )
    .dependsOn(core)

  lazy val sample = play.Project("sample", path = file("sample"))
    .settings(baseSettings: _*)
    .settings(
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
      publishLocal := {},
      publish := {}
    )
//  .settings(
//    (Seq(requireJs += "appLogin.js", requireJsShim += "appLogin.js") ++ closureCompilerSettings(sampleJavascriptOptions)): _*
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
