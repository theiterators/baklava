package pl.iterators.baklava.formatteropenapi.utils.builders

import io.swagger.v3.oas.models.{Components, OpenAPI, Paths}
import io.swagger.v3.oas.models.info.Info

object OpenApiBuilder {
  def build(info: Info, components: Components, paths: Paths): OpenAPI = {
    val openApi = new OpenAPI
    openApi.setInfo(info)
    openApi.setPaths(paths)
    openApi.setComponents(components)
    openApi
  }
}
