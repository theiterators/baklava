package pl.iterators.baklava

import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.BaklavaSerialize.serializableCallCodec
import sttp.model.StatusCode

import java.nio.file.{Files, Path}
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*

class BaklavaSerializeSpec extends AnyFunSpec with Matchers with BeforeAndAfterAll {

  private val suite = "pl.iterators.baklava.BaklavaSerializeSpec.fixture"

  private def request(symbolicPath: String) = BaklavaRequestContext[Nothing, Unit, Unit, Unit, Unit, Unit, Unit](
    symbolicPath = symbolicPath,
    path = symbolicPath,
    pathDescription = None,
    pathSummary = None,
    method = None,
    operationDescription = None,
    operationSummary = None,
    operationId = None,
    operationTags = Seq.empty,
    securitySchemes = Seq.empty,
    body = None,
    bodySchema = None,
    headers = Seq.empty,
    headersDefinition = (),
    headersProvided = (),
    headersSeq = Seq.empty,
    security = AppliedSecurity(NoopSecurity, Map.empty),
    pathParameters = (),
    pathParametersProvided = (),
    pathParametersSeq = Seq.empty,
    queryParameters = (),
    queryParametersProvided = (),
    queryParametersSeq = Seq.empty,
    responseDescription = None,
    responseHeaders = Seq.empty
  )

  private val response = BaklavaResponseContext[Unit, Unit, Unit](
    protocol = BaklavaHttpProtocol("HTTP/1.1"),
    status = StatusCode.Ok,
    headers = Seq.empty,
    body = (),
    rawRequest = (),
    requestBodyString = "",
    rawResponse = (),
    responseBodyString = "",
    requestContentType = None,
    responseContentType = None
  )

  @nowarn("cat=deprecation")
  private def serializeWithoutSuite(symbolicPath: String): Unit =
    BaklavaSerialize.serializeCall(request(symbolicPath), response).get

  private def onlyCallIn(dir: Path): BaklavaSerializableCall = {
    val files = Files.list(dir).iterator().asScala.toList
    files should have size 1
    readFromArray[BaklavaSerializableCall](Files.readAllBytes(files.head))
  }

  private def deleteDir(dir: Path): Unit =
    if (Files.exists(dir)) Files.walk(dir).sorted(java.util.Comparator.reverseOrder[Path]()).iterator().asScala.foreach(Files.delete)

  override def afterAll(): Unit = {
    deleteDir(BaklavaCallStore.default.stagingDir.resolve(suite))
    deleteDir(BaklavaCallStore.default.stagingDir.resolve(BaklavaCallStore.UnscopedSuite))
  }

  describe("BaklavaSerialize.serializeCall") {

    it("writes the call to the default store's staging under the given suite") {
      BaklavaSerialize.serializeCall(suite, request("/serialize-spec"), response).get
      onlyCallIn(BaklavaCallStore.default.stagingDir.resolve(suite)).request.symbolicPath shouldBe "/serialize-spec"
    }

    it("without a suite name writes under the reserved _unscoped suite") {
      serializeWithoutSuite("/unscoped-spec")
      onlyCallIn(
        BaklavaCallStore.default.stagingDir.resolve(BaklavaCallStore.UnscopedSuite)
      ).request.symbolicPath shouldBe "/unscoped-spec"
    }
  }
}
