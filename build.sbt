import com.jsuereth.sbtpgp.PgpKeys
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

val scala_2_12             = "2.12.12"
val scala_2_13             = "2.13.4"
val mainScalaVersion       = scala_2_13
val supportedScalaVersions = Seq(scala_2_12, scala_2_13)

ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion := mainScalaVersion

lazy val baseSettings = Seq(
  organization := "pl.iterators",
  organizationName := "Iterators",
  homepage := Some(url("https://github.com/theiterators/baklava")),
  scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8"),
  scalafmtVersion := "1.3.0",
  crossScalaVersions := supportedScalaVersions,
  scalafmtOnCompile := true, // Sonatype settings
  publishTo := sonatypePublishTo.value,
  sonatypeProfileName := "pl.iterators",
  publishMavenStyle := true,
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  organization := "pl.iterators",
  organizationName := "Iterators",
  organizationHomepage := Some(url("https://www.iteratorshq.com/")),
  developers := List(
    Developer(
      id = "kpalcowski",
      name = "Krzysztof Palcowski",
      email = "kpalcowski@iteratorshq.com",
      url = url("https://github.com/kristerr")
    )
  ),
  scmInfo := Some(
    ScmInfo(
      browseUrl = url("https://github.com/theiterators/baklava"),
      connection = "scm:git:https://github.com/theiterators/baklava.git"
    )
  ),
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  crossScalaVersions := supportedScalaVersions,
  releaseCrossBuild := true
)

val kebsV              = "1.9.1"
val reflectionsVersion = "0.9.12"
val jsonSchemaVersion  = "0.7.1"
val specs2V            = "4.6.0"
val akkaV              = "2.6.1"
val akkaHttpV          = "10.2.1"
val swaggerV           = "2.1.6"
val scalatestV         = "3.2.2"

lazy val core = project
  .in(file("core"))
  .settings(baseSettings: _*)
  .settings(
    name := "core",
    moduleName := "baklava-core"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "pl.iterators"        %% "kebs-spray-json"             % kebsV,
        "pl.iterators"        %% "kebs-tagged-meta"            % kebsV,
        "pl.iterators"        %% "kebs-jsonschema"             % kebsV,
        "pl.iterators"        %% "kebs-scalacheck"             % kebsV,
        "org.reflections"     % "reflections"                  % reflectionsVersion,
        "com.github.andyglow" %% "scala-jsonschema"            % jsonSchemaVersion,
        "com.github.andyglow" %% "scala-jsonschema-spray-json" % jsonSchemaVersion,
        "com.github.andyglow" %% "scala-jsonschema-enumeratum" % jsonSchemaVersion,
        "org.specs2"          %% "specs2-core"                 % specs2V % "test"
      )
    }
  )

lazy val akkahttp = project
  .in(file("akkahttp"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "akkahttp",
    moduleName := "baklava-akkahttp"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "pl.iterators"      %% "kebs-akka-http"    % kebsV,
        "com.typesafe.akka" %% "akka-slf4j"        % akkaV,
        "com.typesafe.akka" %% "akka-stream"       % akkaV,
        "com.typesafe.akka" %% "akka-http-core"    % akkaHttpV,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV
      )
    }
  )

lazy val akkahttpscalatest = project
  .in(file("akkahttpscalatest"))
  .dependsOn(akkahttp % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "akkahttpscalatest",
    moduleName := "baklava-akkahttpscalatest"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "org.scalatest" %% "scalatest" % scalatestV
      )
    }
  )

lazy val akkahttpspecs2 = project
  .in(file("akkahttpspecs2"))
  .dependsOn(akkahttp % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "akkahttpspecs2",
    moduleName := "baklava-akkahttpspecs2"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "org.specs2"        %% "specs2-core"  % specs2V,
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test"
      )
    }
  )

lazy val formatterts = project
  .in(file("formatterts"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "formatterts",
    moduleName := "baklava-formatterts"
  )

lazy val formatteropenapi = project
  .in(file("formatteropenapi"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "formatteropenapi",
    moduleName := "baklava-formatteropenapi"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "io.swagger.core.v3" % "swagger-core" % swaggerV
      )
    }
  )

lazy val baklava = project
  .in(file("."))
  .aggregate(
    core,
    akkahttp,
    akkahttpscalatest,
    akkahttpspecs2,
    formatteropenapi,
    formatterts
  )
  .settings(baseSettings: _*)
  .settings(
    name := "baklava",
    description := "Library to generate docs",
    releaseProcess := Seq(
      checkSnapshotDependencies,
      inquireVersions,
      releaseStepCommandAndRemaining("+publishLocalSigned"),
      releaseStepCommandAndRemaining("+clean"),
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
