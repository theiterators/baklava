package pl.iterators.baklava.formatter

import pl.iterators.baklava.core.model.EnrichedRouteRepresentation

abstract class Formatter {
  def generate(outputPath: String, printableList: List[EnrichedRouteRepresentation[_, _]]): Unit
}
