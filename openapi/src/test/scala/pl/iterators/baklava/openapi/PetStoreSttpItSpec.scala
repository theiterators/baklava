package pl.iterators.baklava.openapi

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.wait.strategy.Wait
import pl.iterators.baklava.scalatest.ScalatestAsExecution
import pl.iterators.baklava.sttp4.{BaklavaSttp, FromSttpBody, ToSttpBody}
import sttp.client4.SyncBackend
import sttp.model.Uri

// remote-API setup reduces to the base URI
trait PetStoreSttpItSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaScalatestInMemory[SyncBackend, ToSttpBody, FromSttpBody]
    with BaklavaSttp[Unit, Unit, ScalatestAsExecution]
    with TestContainerForAll {

  override val containerDef: GenericContainer.Def[GenericContainer] = GenericContainer.Def(
    "swaggerapi/petstore3:unstable",
    exposedPorts = Seq(8080),
    waitStrategy = Wait.forHttp("/")
  )

  override def baseUri: Uri = withContainers { petstore =>
    Uri.unsafeParse(s"http://${petstore.containerIpAddress}:${petstore.mappedPort(8080)}/api/v3")
  }
}
