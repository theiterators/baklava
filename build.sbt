val scala_2_12 = "2.12.12"
val scala_2_13 = "2.13.4"
val mainScalaVersion = scala_2_13
val supportedScalaVersions = Seq(scala_2_12, scala_2_13)

ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion := mainScalaVersion

lazy val baseSettings = Seq(
  organization := "pl.iterators",
  organizationName := "Iterators",
  organizationHomepage := Some(url("https://iterato.rs")),
  version := "0.0.1-SNAPSHOT",
  homepage := Some(url("https://github.com/theiterators/kebs")),
  scalacOptions := Seq("-deprecation",
                       "-unchecked",
                       "-feature",
                       "-encoding",
                       "utf8"),
  scalafmtVersion := "1.3.0",
  crossScalaVersions := supportedScalaVersions,
  scalafmtOnCompile := true,
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

val kebsV = "1.9.1-SNAPSHOT"

lazy val core = project
  .in(file("core"))
  .settings(baseSettings: _*)
  .settings(
    name := "core",
    moduleName := "baklava-core"
  )
  .settings(
    libraryDependencies ++= {
      val reflectionsVersion = "0.9.12"
      val jsonSchemaVersion = "0.5.0"

      Seq(
        "pl.iterators" %% "kebs-spray-json" % kebsV,
        "pl.iterators" %% "kebs-tagged-meta" % kebsV,
        "pl.iterators" %% "kebs-jsonschema" % kebsV,
        "pl.iterators" %% "kebs-scalacheck" % kebsV,
        "org.reflections" % "reflections" % reflectionsVersion,
        "com.github.andyglow" %% "scala-jsonschema" % jsonSchemaVersion,
        "com.github.andyglow" %% "scala-jsonschema-spray-json" % jsonSchemaVersion,
        "com.github.andyglow" %% "scala-jsonschema-enumeratum" % jsonSchemaVersion
      )
    }
  )

lazy val akkahttp = project
  .in(file("akkahttp"))
  .dependsOn(core)
  .settings(baseSettings: _*)
  .settings(
    name := "akkahttp",
    moduleName := "baklava-akkahttp"
  )
  .settings(
    libraryDependencies ++= {
      val akkaV = "2.6.1"
      val akkaHttpV = "10.2.1"
      Seq(
        "pl.iterators" %% "kebs-akka-http" % kebsV,
        "com.typesafe.akka" %% "akka-slf4j" % akkaV,
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-http-core" % akkaHttpV,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV
      )
    }
  )

lazy val akkahttpscalatest = project
  .in(file("akkahttpscalatest"))
  .dependsOn(akkahttp)
  .settings(baseSettings: _*)
  .settings(
    name := "akkahttpscalatest",
    moduleName := "baklava-akkahttpscalatest"
  )
  .settings(
    libraryDependencies ++= {
      val scalatestV = "3.2.2"

      Seq(
        "org.scalatest" %% "scalatest" % scalatestV
      )
    }
  )

lazy val akkahttpspecs2 = project
  .in(file("akkahttpspecs2"))
  .dependsOn(akkahttp)
  .settings(baseSettings: _*)
  .settings(
    name := "akkahttpspecs2",
    moduleName := "baklava-akkahttpspecs2"
  )
  .settings(
    libraryDependencies ++= {
      val specs2V = "4.6.0"

      Seq(
        "org.specs2" %% "specs2-core" % specs2V
      )
    }
  )

lazy val formatterts = project
  .in(file("formatterts"))
  .dependsOn(core)
  .settings(baseSettings: _*)
  .settings(
    name := "formatterts",
    moduleName := "baklava-formatterts"
  )

lazy val formatteropenapi = project
  .in(file("formatteropenapi"))
  .dependsOn(core)
  .settings(baseSettings: _*)
  .settings(
    name := "formatteropenapi",
    moduleName := "baklava-formatteropenapi"
  )
  .settings(
    libraryDependencies ++= {
      val swaggerV = "2.1.6"

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
    description := "Library to generate docs"
  )
