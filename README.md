## Baklava
##### Scala to create different type of documentation (like openapi or ts interfaces) based on routers. Currently it supports akka http and tests written in scalatest or specs2.

A library maintained by [Iterators](https://www.iteratorshq.com).


### Table of contents
* [Installation?](#installation)
* [Usage](#usage)
* [Generation](#generation)

### Installation
First we need to add plugin to ``` plugins.sbt ```
```scala
addSbtPlugin("pl.iterators" % "baklava-sbt-plugin" % "0.2.0")
```
Then we need to make some changes in build.sbt

```scala

val baklavaV = "0.2.0"
libraryDependencies += "pl.iterators"             %% "baklava-akkahttp"         % baklavaV    % "test"
libraryDependencies += "pl.iterators"             %% "baklava-circe"            % baklavaV    % "test"
libraryDependencies += "pl.iterators"             %% "baklava-formatteropenapi" % baklavaV    % "test"
libraryDependencies += "pl.iterators"             %% "baklava-generator"        % baklavaV    % "test"
libraryDependencies += "pl.iterators"             %% "baklava-routes"           % baklavaV
libraryDependencies += "pl.iterators"             %% "baklava-specs2"           % baklavaV    % "test"

enablePlugins(BaklavaSbtPlugin)
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    baklavaTestClassPackage := "pl.iterators.sample", // variable that tells us beginning package of classes that inherits from RouteSpec
    baklavaFormatters := Seq(BaklavaSbtPlugin.model.Formatters.SimpleDocsFormatter, BaklavaSbtPlugin.model.Formatters.OpenApiFormatter)
  )
)

```

### Usage
We will demonstrate usage based on an example with circe.
Consider you have spec named: RouteSpec. In order to use, you need to include proper library in dependency and mix in proper trait to your spec (or create a new one trait, which is preferable if you do not want to extends all tests one time).

```scala
trait RouteDocSpec extends AkkaHttpRouteBaklavaSpec with Specs2RouteBaklavaSpec with ProjectDefinedPredefs with RouteSpec {
  override def shutdownSpec() = TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
}

class HealthCheckRouterSpec extends RouteDocSpec {

  override val routeRepresentation = RouteRepresentation[Unit, Unit]("Health check", "GET", "/health-check")
  
  // base scope has custom UserAuthenticator function which differentiates logged-user based on whether his AuthContext is Some(_) or None
  trait loggedTestCase extends BaseScope {
    override lazy val authContextForTest: Option[AuthContext] = Some(allGenerators[AuthContext].normal.generate)
  }

  val routePath = routeRepresentation.path

  routeRepresentation.name should {
    "return OK for logged user" in new loggedTestCase {
      TestRequest(routePath, emptyString) ~> allRoutes ~> check {
        response.status shouldEqual OK
      }
    }
  }

}
```
where
```scala
trait ProjectDefinedPredefs extends KebsArbitraryPredefs with KebsJsonSchemaPredefs {
  implicit def mapPredef[B1, T1, B2, T2](implicit
                                         schema: _root_.json.schema.Predef[Map[B1, B2]]
                                        ): _root_.json.schema.Predef[Map[B1 @@ T1, B2 @@ T2]] =
    schema.asInstanceOf[_root_.json.schema.Predef[Map[B1 @@ T1, B2 @@ T2]]]
}
```

is based on kebs library (https://github.com/theiterators/kebs) and 
where `RouteSpec` has to be extended with `CirceJsonStringProvider` if we wish to use circe in our tests definitions.

#todo document RouteRepresentation interface

#todo split document into generation and serve parts

#todo describe BaklavaRoutes


### Generation

In sbt shell:
`baklavaGenerate` to generate an output in a form of html files describing each of the endpoints fow which we specified documentation in tests.
Files will be located in ```target.baklava```.
