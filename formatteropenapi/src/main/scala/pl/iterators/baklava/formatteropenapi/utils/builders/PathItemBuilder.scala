package pl.iterators.baklava.formatteropenapi.utils.builders

import io.swagger.v3.oas.models.{Operation, PathItem}
import io.swagger.v3.oas.models.parameters.Parameter

object PathItemBuilder {
  def build(
      parameters: List[Parameter],
      get: Option[Operation],
      post: Option[Operation],
      patch: Option[Operation],
      put: Option[Operation],
      delete: Option[Operation]
  ): PathItem = {
    val pathItem = new PathItem

    get.foreach(pathItem.setGet)
    post.foreach(pathItem.setPost)
    patch.foreach(pathItem.setPatch)
    put.foreach(pathItem.setPut)
    delete.foreach(pathItem.setDelete)

    parameters.foreach { p =>
      pathItem.addParametersItem(p)
    }

    pathItem
  }
}
