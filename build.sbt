ThisBuild / tlBaseVersion    := "2.0"
ThisBuild / versionScheme    := Some("early-semver")
ThisBuild / organization     := "pl.iterators"
ThisBuild / organizationName := "Iterators"
ThisBuild / startYear        := Some(2024)
ThisBuild / licenses         := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("luksow", "Łukasz Sowa")
)

val Scala213 = "2.13.15"
val Scala3   = "3.3.3"
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
ThisBuild / scalaVersion       := Scala213

lazy val baklava = tlCrossRootProject.aggregate(core, pekkohttp, specs2, scalatest, munit)

val pekkoHttpV = "1.1.0"
val pekkoV     = "1.1.2"
val kebsV      = "2.0.0"
val specs2V    = "4.20.9"
val scalatestV = "3.2.19"
val munitV     = "1.0.2"

lazy val core = project
  .in(file("core"))
  .settings(
    name := "baklava2-core",
    libraryDependencies ++= Seq(
      "pl.iterators" %% "kebs-core" % kebsV
    )
  )

lazy val pekkohttp = project
  .in(file("pekkohttp"))
  .dependsOn(core)
  .settings(
    name := "baklava2-pekko-http",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http"         % pekkoHttpV,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV,
      "org.apache.pekko" %% "pekko-stream"       % pekkoV
    )
  )

lazy val specs2 = project
  .in(file("specs2"))
  .dependsOn(core)
  .settings(
    name := "baklava2-specs2",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % specs2V
    )
  )

lazy val scalatest = project
  .in(file("scalatest"))
  .dependsOn(core)
  .settings(
    name := "baklava2-scalatest",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestV
    )
  )

lazy val munit = project
  .in(file("munit"))
  .dependsOn(core)
  .settings(
    name := "baklava2-munit",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitV
    )
  )

Test / scalafmtOnCompile      := true
ThisBuild / scalafmtOnCompile := true
