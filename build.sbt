ThisBuild / tlBaseVersion          := "1.0"
ThisBuild / tlCiHeaderCheck        := false
ThisBuild / tlUntaggedAreSnapshots := true
ThisBuild / versionScheme          := Some("early-semver")
ThisBuild / organization           := "pl.iterators"
ThisBuild / organizationName       := "Iterators"
ThisBuild / startYear              := Some(2024)
ThisBuild / licenses               := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("luksow", "Łukasz Sowa")
)
ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatypeLegacy

val Scala213 = "2.13.16"
val Scala3   = "3.3.3"
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
ThisBuild / scalaVersion       := Scala213

scalacOptions += "-Xmax-inlines:64"
// TODO: add -Yretain-trees to scalacOptions to enable magnolia features

lazy val baklava = tlCrossRootProject.aggregate(core, openapi, pekkohttp, pekkohttproutes, http4s, specs2, scalatest, munit, sbtplugin)

val swaggerV       = "2.2.27"
val swaggerParserV = "2.1.24"
val pekkoHttpV     = "1.1.0"
val pekkoV         = "1.1.2"
val kebsV          = "2.0.0"
val circeV         = "0.14.0"
val specs2V        = "4.20.9"
val scalatestV     = "3.2.19"
val munitV         = "1.0.2"
val http4sV        = "0.23.29"
val reflectionsV   = "0.10.2"
val magnoliaS2V    = "1.1.10"
val magnoliaS3V    = "1.3.8"

val enumeratumV     = "1.7.5"
val pekkoHttpJsonV  = "3.0.0"
val testcontainersV = "0.41.8"

val webjarsLocatorV = "0.52"
val swaggerUiV      = "5.17.11"
val typesafeConfigV = "1.4.3"
val sttpModelV      = "1.7.13"

lazy val core = project
  .in(file("core"))
  .settings(
    name := "baklava-core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.model" %% "core" % sttpModelV,
      "pl.iterators"   %% "kebs-core"    % kebsV,
      "org.reflections" % "reflections"  % reflectionsV,
      "pl.iterators"   %% "kebs-circe"   % kebsV,
      "io.circe"       %% "circe-parser" % circeV,
      if (scalaVersion.value.startsWith("3")) "com.softwaremill.magnolia1_3" %% "magnolia" % magnoliaS3V
      else "com.softwaremill.magnolia1_2"                                    %% "magnolia" % magnoliaS2V
    )
  )

lazy val openapi = project
  .in(file("openapi"))
  .dependsOn(core, pekkohttp % "test", http4s % "test", scalatest % "test")
  .settings(
    name := "baklava-openapi",
    libraryDependencies ++= Seq(
      "io.swagger.core.v3"    % "swagger-core"                   % swaggerV,
      "io.swagger.parser.v3"  % "swagger-parser"                 % swaggerParserV,
      "com.beachape"         %% "enumeratum"                     % enumeratumV     % "test",
      "pl.iterators"         %% "kebs-enumeratum"                % kebsV           % "test",
      "pl.iterators"         %% "kebs-circe"                     % kebsV           % "test",
      "com.github.pjfanning" %% "pekko-http-circe"               % pekkoHttpJsonV  % "test",
      "org.http4s"           %% "http4s-ember-client"            % http4sV         % "test",
      "org.http4s"           %% "http4s-circe"                   % http4sV         % "test",
      "com.dimafeng"         %% "testcontainers-scala-scalatest" % testcontainersV % "test"
    )
  )

lazy val pekkohttp = project
  .in(file("pekkohttp"))
  .dependsOn(core)
  .settings(
    name := "baklava-pekko-http",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http"         % pekkoHttpV,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV,
      "org.apache.pekko" %% "pekko-stream"       % pekkoV
    )
  )

lazy val pekkohttproutes = project
  .in(file("pekkohttproutes"))
  .settings(
    name := "baklava-pekko-http-routes",
    libraryDependencies ++= {
      Seq(
        "org.apache.pekko"    %% "pekko-stream"    % pekkoHttpV,
        "org.apache.pekko"    %% "pekko-http"      % pekkoHttpV,
        "com.typesafe"         % "config"          % typesafeConfigV,
        "org.webjars"          % "webjars-locator" % webjarsLocatorV,
        "io.swagger.core.v3"   % "swagger-core"    % swaggerV,
        "io.swagger.parser.v3" % "swagger-parser"  % swaggerParserV,
        "org.webjars"          % "swagger-ui"      % swaggerUiV
      )
    }
  )

lazy val http4s = project
  .in(file("http4s"))
  .dependsOn(core)
  .settings(
    name := "baklava-http4s",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sV
    )
  )

lazy val specs2 = project
  .in(file("specs2"))
  .dependsOn(core)
  .settings(
    name := "baklava-specs2",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % specs2V
    )
  )

lazy val scalatest = project
  .in(file("scalatest"))
  .dependsOn(core)
  .settings(
    name := "baklava-scalatest",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestV
    )
  )

lazy val munit = project
  .in(file("munit"))
  .dependsOn(core)
  .settings(
    name := "baklava-munit",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitV
    )
  )

lazy val sbtplugin = project
  .in(file("sbtplugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name               := "baklava-sbt-plugin",
    scalaVersion       := "2.12.17",
    crossScalaVersions := Seq("2.12.17"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.3.10" // set minimum sbt version
      }
    }
  )

Test / scalafmtOnCompile      := true
ThisBuild / scalafmtOnCompile := true
