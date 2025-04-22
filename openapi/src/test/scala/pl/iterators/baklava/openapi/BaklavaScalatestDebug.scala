package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{BaklavaHttpDsl, BaklavaTestFrameworkDslDebug}

trait BaklavaScalatestDebug[
    RouteType,
    ToRequestBodyType[_],
    FromResponseBodyType[_]
] extends BaklavaScalatest[
      RouteType,
      ToRequestBodyType,
      FromResponseBodyType
    ]
    with BaklavaTestFrameworkDslDebug[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, ScalatestAsExecution] {
  this: BaklavaHttpDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, ScalatestAsExecution] =>

  override def afterAll(): Unit = {
    val openAPI = new OpenAPI()
    BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, listCalls)
    println(Yaml.pretty(openAPI))
  }
}
