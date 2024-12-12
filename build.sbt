import sbt.internal.util.Attributed.data

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

lazy val baklava = tlCrossRootProject.aggregate(core, openapi, pekkohttp, http4s, specs2, scalatest, munit)

val reflectionsV = "0.10.2"
val swaggerV     = "2.2.27"
val pekkoHttpV   = "1.1.0"
val pekkoV       = "1.1.2"
val kebsV        = "2.0.0"
val specs2V      = "4.20.9"
val scalatestV   = "3.2.19"
val munitV       = "1.0.2"
val http4sV      = "0.23.29"

val enumeratumV    = "1.7.5"
val pekkoHttpJsonV = "3.0.0"

lazy val core = project
  .in(file("core"))
  .settings(
    name := "baklava2-core",
    libraryDependencies ++= Seq(
      "pl.iterators"   %% "kebs-core"   % kebsV,
      "org.reflections" % "reflections" % reflectionsV
    )
  )

lazy val openapi = project
  .in(file("openapi"))
  .dependsOn(core, pekkohttp % "test", specs2 % "test")
  .settings(
    name := "baklava2-openapi",
    libraryDependencies ++= Seq(
      "io.swagger.core.v3"    % "swagger-core"     % swaggerV,
      "com.beachape"         %% "enumeratum"       % enumeratumV    % "test",
      "pl.iterators"         %% "kebs-enumeratum"  % kebsV          % "test",
      "pl.iterators"         %% "kebs-circe"       % kebsV          % "test",
      "com.github.pjfanning" %% "pekko-http-circe" % pekkoHttpJsonV % "test"
    ),
    Test / testOptions += Tests.Cleanup { () => //to trzeba bedzie dodac autopluginem - i tylko to - ale to tak czy siak nei powinno byc w tym repo i jest tylko dla wygody testow
      //poza tym do dyskusji czy to robic tak (bo to rzuca warny w tkaiej postaci, czy jednak osobnym taskiem baklavaGenerate (wtedy nie rzuca))
      println(s"Executing cleanup - baklava generate")
      val clazz = "pl.iterators.baklava.BaklavaGenerate"

      val configurationClassPath = (fullClasspath in Test).value
      val r                      = (runner in (Test, run)).value
      val s                      = streams.value
      r.run(clazz, data(configurationClassPath), Nil, s.log).get
    }
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

lazy val http4s = project
  .in(file("http4s"))
  .dependsOn(core)
  .settings(
    name := "baklava2-http4s",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sV
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
