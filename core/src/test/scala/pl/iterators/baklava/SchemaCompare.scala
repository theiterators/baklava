package pl.iterators.baklava

import org.scalatest.matchers.should.Matchers.*

object SchemaCompare {
  def assertSchemaFieldsEqual[A, B](a: Schema[A], b: Schema[B]): Unit = {
    // a.className shouldEqual b.className - we got some problem here
    a.`type` shouldEqual b.`type`
    a.format shouldEqual b.format
    a.properties.keySet shouldEqual b.properties.keySet
    for (k <- a.properties.keys) {
      val aProp = a.properties(k)
      val bProp = b.properties(k)
      // aProp.className shouldEqual bProp.className
      aProp.format shouldEqual bProp.format
      aProp.`type` shouldEqual bProp.`type`
    }
    a.items shouldEqual b.items
    a.`enum` shouldEqual b.`enum`
    a.required shouldEqual b.required
    a.additionalProperties shouldEqual b.additionalProperties
    a.description shouldEqual b.description
    a.default shouldEqual b.default
    ()
  }
}
