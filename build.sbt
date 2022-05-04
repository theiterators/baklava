import com.jsuereth.sbtpgp.PgpKeys
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

val scala_2_12             = "2.12.12"
val scala_2_13             = "2.13.4"
val mainScalaVersion       = scala_2_13
val supportedScalaVersions = Seq(scala_2_12, scala_2_13)

ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion := mainScalaVersion
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

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

val akkaV              = "2.6.19"
val akkaHttpV          = "10.2.9"
val typesafeConfigV    = "1.4.2"
val kebsV              = "1.9.4"
val reflectionsVersion = "0.9.12"
val jsonSchemaVersion  = "0.7.1"
val specs2V            = "4.6.0"
val swaggerV           = "2.1.6"
val scalatestV         = "3.2.2"
val webjarsLocatorV    = "0.45"
val swaggerUiV         = "3.40.0" //unfortunately we need to stuck with this version

lazy val routes = project
  .in(file("routes"))
  .settings(baseSettings: _*)
  .settings(
    name := "routes",
    moduleName := "baklava-routes"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "com.typesafe.akka" %% "akka-http"      % akkaHttpV,
        "com.typesafe"      % "config"          % typesafeConfigV,
        "org.webjars"       % "webjars-locator" % webjarsLocatorV,
        "org.webjars"       % "swagger-ui"      % swaggerUiV
      )
    }
  )

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
        "pl.iterators"        %% "kebs-tagged-meta"            % kebsV,
        "pl.iterators"        %% "kebs-jsonschema"             % kebsV,
        "pl.iterators"        %% "kebs-scalacheck"             % kebsV,
        "com.github.andyglow" %% "scala-jsonschema"            % jsonSchemaVersion,
        "com.github.andyglow" %% "scala-jsonschema-enumeratum" % jsonSchemaVersion,
        "org.reflections"     % "reflections"                  % reflectionsVersion,
        "org.specs2"          %% "specs2-core"                 % specs2V % "test"
      )
    }
  )

lazy val circe = project
  .in(file("circe"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "circe",
    moduleName := "baklava-circe"
  )
  .settings(
    libraryDependencies ++= {
      Seq(
        "pl.iterators" %% "kebs-circe" % kebsV
      )
    }
  )

lazy val sprayjson = project
  .in(file("sprayjson"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "sprayjson",
    moduleName := "baklava-sprayjson"
  )
  .settings(
    libraryDependencies ++= {
      Seq(
        "pl.iterators" %% "kebs-spray-json" % kebsV
      )
    }
  )

lazy val formatter = project
  .in(file("formatter"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(circe % "test->test")
  .dependsOn(sprayjson % "test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "formatter",
    moduleName := "baklava-formatter"
  )

lazy val formatteropenapi = project
  .in(file("formatteropenapi"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(formatter % "compile->compile;test->test")
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

lazy val generator = project
  .in(file("generator"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(formatter % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "generator",
    moduleName := "baklava-generator"
  )
  .settings(
    libraryDependencies ++= {
      Seq(
        "org.reflections" % "reflections" % reflectionsVersion
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
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
      )
    }
  )

lazy val scalatest = project
  .in(file("scalatest"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(akkahttp % "test->test")
  .dependsOn(circe % "test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "scalatest",
    moduleName := "baklava-scalatest"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "org.scalatest" %% "scalatest" % scalatestV
      )
    }
  )

lazy val specs2 = project
  .in(file("specs2"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(sprayjson % "test->test")
  .settings(baseSettings: _*)
  .settings(
    name := "specs2",
    moduleName := "baklava-specs2"
  )
  .settings(
    libraryDependencies ++= {

      Seq(
        "org.specs2"        %% "specs2-core"         % specs2V,
        "com.typesafe.akka" %% "akka-stream"         % akkaV % "test",
        "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test",
        "com.typesafe.akka" %% "akka-testkit"        % akkaV % "test",
        "com.typesafe.akka" %% "akka-http-core"      % akkaHttpV % "test",
        "com.typesafe.akka" %% "akka-http-testkit"   % akkaHttpV % "test"
      )
    }
  )

lazy val sbtplugin = project
  .in(file("sbtplugin"))
  .enablePlugins(SbtPlugin)
  .settings(baseSettings: _*)
  .settings(
    name := "sbt-plugin",
    moduleName := "baklava-sbt-plugin",
    scalaVersion := "2.12.12",
    crossScalaVersions := Seq("2.12.12"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.3.10" // set minimum sbt version
      }
    }
  )

lazy val baklava = project
  .in(file("."))
  .aggregate(
    routes,
    core,
    circe,
    sprayjson,
    formatter,
    formatteropenapi,
    generator,
    akkahttp,
    scalatest,
    specs2,
    sbtplugin
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
