import com.jsuereth.sbtpgp.PgpKeys

val scala_2_13             = "2.13.15"
val mainScalaVersion       = scala_2_13
val supportedScalaVersions = Seq(scala_2_13)

ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion := mainScalaVersion
ThisBuild / versionScheme := Some("early-semver")
// ThisBuild / sonatypeCredentialHost := "oss.sonatype.org"
// ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
ThisBuild / resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val baseSettings = Seq(
  organization := "pl.iterators",
  organizationName := "Iterators",
  homepage := Some(url("https://github.com/theiterators/baklava")),
  scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8"),
  crossScalaVersions := supportedScalaVersions,
  scalafmtOnCompile := true, // Sonatype settings
  sonatypeProfileName := "pl.iterators",
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  organization := "pl.iterators",
  organizationName := "Iterators",
  organizationHomepage := Some(url("https://www.iteratorshq.com/")),
  developers := List(
    Developer(id = "kpalcowski", name = "Krzysztof Palcowski", email = "kpalcowski@iteratorshq.com", url = url("https://github.com/kristerr"))
  ),
  scmInfo := Some(
    ScmInfo(browseUrl = url("https://github.com/theiterators/baklava"), connection = "scm:git:https://github.com/theiterators/baklava.git")
  ),
//   credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  crossScalaVersions := supportedScalaVersions
)

val akkaV           = "2.6.21"
val akkaHttpV       = "10.2.10"
val pekkoV          = "1.1.2"
val pekkoHttpV      = "1.1.0"
val http4sStirV     = "0.4.0"
val typesafeConfigV = "1.4.3"
val kebsV           = "2.0.0"
val reflectionsV    = "0.10.2"
val specs2V         = "4.20.9"
val jsonSchemaV     = "0.7.11"
val swaggerV        = "2.2.25"
val scalatestV      = "3.2.19"
val webjarsLocatorV = "0.52"
val swaggerUiV      = "3.40.0" //unfortunately we need to stuck with this version

lazy val akkahttproutes = project
  .in(file("akka-http-routes"))
  .settings(baseSettings: _*)
  .settings(name := "akka-http-routes", moduleName := "baklava-akka-http-routes")
  .settings(libraryDependencies ++= {
    Seq(
      "com.typesafe.akka" %% "akka-http"       % akkaHttpV,
      "com.typesafe"       % "config"          % typesafeConfigV,
      "org.webjars"        % "webjars-locator" % webjarsLocatorV,
      "org.webjars"        % "swagger-ui"      % swaggerUiV
    )
  })

lazy val pekkohttproutes = project
  .in(file("pekko-http-routes"))
  .settings(baseSettings: _*)
  .settings(name := "pekko-http-routes", moduleName := "baklava-pekko-http-routes")
  .settings(libraryDependencies ++= {
    Seq(
      "org.apache.pekko" %% "pekko-http"      % pekkoHttpV,
      "com.typesafe"      % "config"          % typesafeConfigV,
      "org.webjars"       % "webjars-locator" % webjarsLocatorV,
      "org.webjars"       % "swagger-ui"      % swaggerUiV
    )
  })

lazy val http4sstirroutes = project
  .in(file("http4s-stir-routes"))
  .settings(baseSettings: _*)
  .settings(name := "http4s-stir-routes", moduleName := "baklava-http4s-stir-routes")
  .settings(libraryDependencies ++= {

    Seq(
      "pl.iterators" %% "http4s-stir"     % http4sStirV,
      "com.typesafe"  % "config"          % typesafeConfigV,
      "org.webjars"   % "webjars-locator" % webjarsLocatorV,
      "org.webjars"   % "swagger-ui"      % swaggerUiV
    )
  })

lazy val core = project
  .in(file("core"))
  .settings(baseSettings: _*)
  .settings(name := "core", moduleName := "baklava-core")
  .settings(libraryDependencies ++= {
    Seq(
      "pl.iterators"        %% "kebs-tagged-meta"            % kebsV,
      "pl.iterators"        %% "kebs-jsonschema"             % kebsV,
      "pl.iterators"        %% "kebs-scalacheck"             % kebsV,
      "com.github.andyglow" %% "scala-jsonschema"            % jsonSchemaV,
      "com.github.andyglow" %% "scala-jsonschema-enumeratum" % jsonSchemaV,
      "org.reflections"      % "reflections"                 % reflectionsV,
      "org.specs2"          %% "specs2-core"                 % specs2V % "test"
    )
  })

lazy val circe = project
  .in(file("circe"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(name := "circe", moduleName := "baklava-circe")
  .settings(libraryDependencies ++= {
    Seq("pl.iterators" %% "kebs-circe" % kebsV)
  })

lazy val sprayjson = project
  .in(file("sprayjson"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(name := "sprayjson", moduleName := "baklava-sprayjson")
  .settings(libraryDependencies ++= {
    Seq("pl.iterators" %% "kebs-spray-json" % kebsV)
  })

lazy val formatter = project
  .in(file("formatter"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(circe % "test->test")
  .dependsOn(sprayjson % "test->test")
  .settings(baseSettings: _*)
  .settings(name := "formatter", moduleName := "baklava-formatter")

lazy val formatteropenapi = project
  .in(file("formatter-openapi"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(formatter % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(name := "formatter-openapi", moduleName := "baklava-formatter-openapi")
  .settings(libraryDependencies ++= {
    Seq("io.swagger.core.v3" % "swagger-core" % swaggerV)
  })

lazy val generator = project
  .in(file("generator"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(formatter % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(name := "generator", moduleName := "baklava-generator")
  .settings(libraryDependencies ++= {
    Seq("org.reflections" % "reflections" % reflectionsV)
  })

lazy val akkahttp = project
  .in(file("akka-http"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(name := "akka-http", moduleName := "baklava-akka-http")
  .settings(libraryDependencies ++= {

    Seq(
      "pl.iterators"      %% "kebs-akka-http"    % kebsV,
      "com.typesafe.akka" %% "akka-slf4j"        % akkaV,
      "com.typesafe.akka" %% "akka-stream"       % akkaV,
      "com.typesafe.akka" %% "akka-http-core"    % akkaHttpV,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
    )
  })

lazy val pekkohttp = project
  .in(file("pekko-http"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(name := "pekko-http", moduleName := "baklava-pekko-http")
  .settings(libraryDependencies ++= {

    Seq(
      "pl.iterators"     %% "kebs-pekko-http"    % kebsV,
      "org.apache.pekko" %% "pekko-slf4j"        % pekkoV,
      "org.apache.pekko" %% "pekko-stream"       % pekkoV,
      "org.apache.pekko" %% "pekko-http-core"    % pekkoHttpV,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV % "test"
    )
  })

lazy val http4sstir = project
  .in(file("http4s-stir"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(baseSettings: _*)
  .settings(name := "http4s-stir", moduleName := "baklava-http4s-stir")
  .settings(
    libraryDependencies ++= {

      Seq("pl.iterators" %% "http4s-stir" % http4sStirV, "pl.iterators" %% "http4s-stir-testkit" % http4sStirV)
    },
    crossScalaVersions := Seq(scala_2_13)
  )

lazy val scalatest = project
  .in(file("scalatest"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(akkahttp % "test->test")
  .dependsOn(circe % "test->test")
  .settings(baseSettings: _*)
  .settings(name := "scalatest", moduleName := "baklava-scalatest")
  .settings(libraryDependencies ++= {

    Seq("org.scalatest" %% "scalatest" % scalatestV)
  })

lazy val specs2 = project
  .in(file("specs2"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(sprayjson % "test->test")
  .settings(baseSettings: _*)
  .settings(name := "specs2", moduleName := "baklava-specs2")
  .settings(libraryDependencies ++= {

    Seq(
      "org.specs2"        %% "specs2-core"         % specs2V,
      "com.typesafe.akka" %% "akka-stream"         % akkaV     % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaV     % "test",
      "com.typesafe.akka" %% "akka-testkit"        % akkaV     % "test",
      "com.typesafe.akka" %% "akka-http-core"      % akkaHttpV % "test",
      "com.typesafe.akka" %% "akka-http-testkit"   % akkaHttpV % "test"
    )
  })

lazy val sbtplugin = project
  .in(file("sbtplugin"))
  .enablePlugins(SbtPlugin)
  .settings(baseSettings: _*)
  .settings(
    name := "sbt-plugin",
    moduleName := "baklava-sbt-plugin",
    scalaVersion := "2.12.17",
    crossScalaVersions := Seq("2.12.17"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.3.10" // set minimum sbt version
      }
    }
  )

lazy val baklava = project
  .in(file("."))
  .aggregate(
    core,
    circe,
    sprayjson,
    formatter,
    formatteropenapi,
    generator,
    akkahttp,
    akkahttproutes,
    http4sstir,
    http4sstirroutes,
    pekkohttp,
    pekkohttproutes,
    scalatest,
    specs2,
    sbtplugin
  )
  .settings(baseSettings: _*)
  .settings(name := "baklava", description := "Library to generate docs")
