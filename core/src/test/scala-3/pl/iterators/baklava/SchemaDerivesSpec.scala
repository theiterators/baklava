package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

object SchemaDerivesFixtures {
  final case class Address(street: String, zip: Option[String] = None) derives Schema
  final case class Person(name: String, address: Address, previous: List[Address]) derives Schema
}

class SchemaDerivesSpec extends AnyFunSpec with Matchers {
  import SchemaDerivesFixtures.*

  describe("derives Schema") {
    it("puts a derived instance in the companion that nested derivations pick up") {
      val person = summon[Schema[Person]]
      person.properties("address") should be theSameInstanceAs summon[Schema[Address]]
      person.properties("previous").items.get should be theSameInstanceAs summon[Schema[Address]]
      summon[Schema[Address]].properties("zip").default shouldBe Some(None)
    }
  }
}
