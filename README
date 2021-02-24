## Baklava
##### Scala to create different type of documentation (like openapi or ts interfaces) based on routers. Currently it supports akka http and tests written in scalatest or specs2.

A library maintained by [Iterators](https://www.iteratorshq.com).


### Table of contents
* [Installation?](#installation)
* [Usage](#usage)
* [Generation](#generation)

### Installation

Changes need to be done in build.sbt

```scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

val baklavaV = "0.0.1-SNAPSHOT"
libraryDependencies += "pl.iterators"  %% "baklava-core"               % baklavaV  % "test"
libraryDependencies += "pl.iterators"  %% "baklava-akkahttpscalatest"  % baklavaV  % "test" [optional - if you use scalatest]
libraryDependencies += "pl.iterators"  %% "baklava-akkahttpspecs2"     % baklavaV  % "test" [optional - if you use specs2]
libraryDependencies += "pl.iterators"  %% "baklava-formatteropenapi"   % baklavaV  % "test" [optional - if you want to generate openapi]
libraryDependencies += "pl.iterators"  %% "baklava-formatterts"        % baklavaV  % "test" [optional - if you want to generate ts interfaces]

val generateOutputFromRouteDocSpec = inputKey[Unit]("Generate output from route spec")
fullRunInputTask(generateOutputFromRouteDocSpec, Test, "pl.iterators.baklava.core.GenerateOutputFromRouteDocSpec")
fork in generateOutputFromRouteDocSpec := false

```


### Usage

Consider you have spec named: RouteSpec. In order to use, you need to include proper library in dependency and mix in proper trait to your spec (or create a new one trait, which is preferable if you do not want to extends all tests one time).

```scala
trait RouteDocSpec extends LibraryAkkaHttpSpecs2RouteDocSpec with KebsArbitraryPredefs with KebsJsonSchemaPredefs with RouteSpec {
  override def shutdownSpec() = TestKit.shutdownActorSystem(system,  verifySystemShutdown = true)
}

class GetHealthcheckRouteSpec extends RouteDocSpec {

  override val routeRepresentation = RouteRepresentation[Unit, TimeResult](
    "Returns db status",
    "GET",
    "/healthcheck"
  )
...
}

```

#todo more examples

#todo document RouteRepresentation interface


### Generation

In sbt shell:
`generateOutputFromRouteDocSpec [packageName] [outputDir] [fetcher] [formatter]`

where:

packageName - the package root of your application

outputDir - the directory where you want to generate doc into

fetcher - the fetcher class you want to use for fetch. each of them are provided by separate subprojects.
available options:
- AkkaHttpScalatestFetcher - provided by baklava-akkahttpscalatest
- AkkaHttpSpecs2Fetcher - provided by baklava-akkahttpspecs2

formatter - the formatter class you want to use for generation. each of them are provided by separate subprojects.
available options:
- SimpleOutputFormatter - provided by baklava-core
- OpenApiFormatter - provided by baklava-formatteropenapi
- TsFormatter - provided by baklava-formatterts
- TsStrictFormatter - provided by baklava-formatterts
