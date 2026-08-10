package pl.iterators.baklava.openapi

import io.circe.parser.parse
import io.swagger.v3.oas.models.OpenAPI

import scala.jdk.CollectionConverters.*

// Structural checks for suites whose live responses preclude a deterministic gold file.
object OpenAPIInvariants {

  // #120/#129 guard: a String example is an offender only when it parses to a JSON object/array
  def assertJsonExamplesAreStructured(openAPI: OpenAPI): Unit = {
    val offenders =
      for {
        (path, item) <- Option(openAPI.getPaths).map(_.asScala.toList).getOrElse(Nil)
        (method, op) <- item.readOperationsMap().asScala.toList
        content      <- Option(op.getRequestBody).flatMap(rb => Option(rb.getContent)).toList ++
          Option(op.getResponses).map(_.asScala.values.toList.flatMap(r => Option(r.getContent))).getOrElse(Nil)
        (mediaTypeName, mt) <- content.asScala.toList
        if mediaTypeName == "application/json" || mediaTypeName.endsWith("+json")
        (key, example) <- Option(mt.getExamples).map(_.asScala.toList).getOrElse(Nil)
        value          <- Option(example.getValue).toList
        if value.isInstanceOf[String] && parse(value.asInstanceOf[String]).exists(j => j.isObject || j.isArray)
      } yield s"$method $path -> $mediaTypeName -> $key"

    if (offenders.nonEmpty)
      throw new AssertionError(
        ("application/json examples emitted as strings instead of structured values (#120):" +: offenders).mkString("\n  ")
      )
  }
}
