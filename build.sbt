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

val Scala213 = "2.13.16"
val Scala3   = "3.3.3"
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
ThisBuild / scalaVersion       := Scala213
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
ThisBuild / tlMimaPreviousVersions := Set.empty

scalacOptions += "-Xmax-inlines:64"
// TODO: add -Yretain-trees to scalacOptions to enable magnolia features

lazy val noPublishSettings =
  Seq(
    publishArtifact := false
  )

lazy val perScalaVersionTestSources = Test / unmanagedSourceDirectories ++= {
  val base = baseDirectory.value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _)) => Seq(base / "src" / "test" / "scala-3")
    case Some((2, _)) => Seq(base / "src" / "test" / "scala-2")
    case _            => Seq()
  }
}

lazy val baklava =
  tlCrossRootProject.aggregate(core, simple, openapi, tsrest, pekkohttp, pekkohttproutes, http4s, specs2, scalatest, munit, sbtplugin)

val swaggerV       = "2.2.27"
val swaggerParserV = "2.1.24"
val pekkoHttpV     = "1.1.0"
val pekkoV         = "1.1.2"
val kebsV          = "2.1.3"
val circeV         = "0.14.0"
val jsoniterV      = "2.13.8"
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

lazy val core = project
  .in(file("core"))
  .settings(
    name := "baklava-core",
    perScalaVersionTestSources,
    libraryDependencies ++=
      Seq(
        "org.reflections"                        % "reflections"           % reflectionsV,
        "io.circe"                              %% "circe-parser"          % circeV,
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsoniterV,
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterV  % "provided",
        "org.scalatest"                         %% "scalatest"             % scalatestV % "test",
        if (scalaVersion.value.startsWith("3")) "com.softwaremill.magnolia1_3" %% "magnolia" % magnoliaS3V
        else "com.softwaremill.magnolia1_2"                                    %% "magnolia" % magnoliaS2V
      ) ++ (
        if (scalaVersion.value.startsWith("3")) {
          Seq("pl.iterators" %% "kebs-opaque" % kebsV % "test", "pl.iterators" %% "kebs-baklava" % kebsV % "test")
        } else
          Seq(
            "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
            "org.scala-lang" % "scala-reflect"  % scalaVersion.value
          )
      )
  )

lazy val simple = project
  .in(file("simple"))
  .dependsOn(core, scalatest % "test")
  .settings(
    name := "baklava-simple"
  )

lazy val tsrest = project
  .in(file("tsrest"))
  .dependsOn(core, scalatest % "test")
  .settings(
    name := "baklava-tsrest"
  )

lazy val openapi = project
  .in(file("openapi"))
  .dependsOn(core, pekkohttp % "test", http4s % "test", scalatest % "test")
  .settings(
    name := "baklava-openapi",
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) => Seq("-Xmax-inlines:64")
        case _            => Nil
      }
    },
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

lazy val docs = project
  .in(file("baklava-docs"))
  .dependsOn(core % "test->test;compile->compile")
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(noPublishSettings *)
  .settings(
    name        := "docs",
    description := "Baklava documentation",
    moduleName  := "baklava-docs"
  )
  .settings(
    mdocVariables := Map(
      "VERSION" -> version.value
    )
  )

Test / scalafmtOnCompile      := true
ThisBuild / scalafmtOnCompile := true
