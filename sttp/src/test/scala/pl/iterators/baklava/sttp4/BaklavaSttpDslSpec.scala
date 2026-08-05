package pl.iterators.baklava.sttp4

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{BaklavaAssertionException, EmptyBody, FormOf, Multipart, TextPart}
import sttp.client4.SyncBackend
import sttp.model.{Header => SttpHeader, Method, StatusCode, Uri}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

// Greeting (with its circe codecs) is defined in BaklavaSttpRequestBuildingSpec.scala — same package

class BaklavaSttpDslSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaSttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[SyncBackend, ToSttpBody, FromSttpBody] {

  // InputStream.readAllBytes is Java 9+; the build targets an older release, so read manually
  private def readAllBytes(in: java.io.InputStream): Array[Byte] = {
    val out = new java.io.ByteArrayOutputStream()
    val buf = new Array[Byte](4096)
    var n   = in.read(buf)
    while (n != -1) {
      out.write(buf, 0, n)
      n = in.read(buf)
    }
    out.toByteArray
  }

  // com.sun.net.httpserver.Headers only normalizes the leading char of a header name, so a
  // client sending lower-case names (e.g. over h2c) needs a case-insensitive lookup here
  private def firstHeaderCI(headers: com.sun.net.httpserver.Headers, name: String): Option[String] =
    headers.entrySet.asScala.find(_.getKey.equalsIgnoreCase(name)).flatMap(_.getValue.asScala.headOption)

  // HTTP header names are case-insensitive; the sttp client backend normalizes response headers to lower-case
  private def findHeader(headers: Seq[SttpHeader], name: String): Option[String] =
    headers.find(_.name.equalsIgnoreCase(name)).map(_.value)

  // echoes the request back: body verbatim, request metadata in X-Req-* response headers
  private val server: HttpServer = {
    val s = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    s.createContext(
      "/echo",
      (exchange: HttpExchange) => {
        val body    = readAllBytes(exchange.getRequestBody)
        val headers = exchange.getResponseHeaders
        headers.set("X-Req-Method", exchange.getRequestMethod)
        headers.set("X-Req-Uri", exchange.getRequestURI.toString)
        firstHeaderCI(exchange.getRequestHeaders, "Content-Type").foreach(headers.set("X-Req-Content-Type", _))
        firstHeaderCI(exchange.getRequestHeaders, "X-Default").foreach(headers.set("X-Req-X-Default", _))
        headers.set("Content-Type", "text/plain; charset=UTF-8")
        exchange.sendResponseHeaders(200, if (body.isEmpty) -1 else body.length.toLong)
        if (body.nonEmpty) exchange.getResponseBody.write(body)
        exchange.close()
      }
    )
    s.createContext(
      "/greeting",
      (exchange: HttpExchange) => {
        val payload = """{"hello":"world"}""".getBytes(UTF_8)
        exchange.getResponseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, payload.length.toLong)
        exchange.getResponseBody.write(payload)
        exchange.close()
      }
    )
    s.createContext(
      "/no-content",
      (exchange: HttpExchange) => {
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
      }
    )
    s.start()
    s
  }

  override def baseUri: Uri                    = Uri.unsafeParse(s"http://localhost:${server.getAddress.getPort}")
  override def defaultHeaders: Seq[SttpHeader] = Seq(SttpHeader("X-Default", "on"))

  override def afterAll(): Unit = {
    server.stop(0)
    super.afterAll()
  }

  it("defaults strict header checking to off") {
    strictHeaderCheckDefault shouldBe false
  }

  path("/greeting")(
    supports(Method.GET, summary = "JSON greeting")(
      onRequest.respondsWith[Greeting](StatusCode.Ok, description = "decodes JSON via circe").assert { ctx =>
        val response = ctx.performRequest(defaultBackend)
        response.body shouldBe Greeting("world")
        response.responseContentType shouldBe Some("application/json")
      }
    )
  )

  path("/echo")(
    supports(Method.POST, summary = "echo")(
      onRequest(body = Greeting("hi")).respondsWith[String](StatusCode.Ok, description = "JSON request body").assert { ctx =>
        val response = ctx.performRequest(defaultBackend)
        response.body shouldBe """{"hello":"hi"}"""
        // header names are case-insensitive over the wire; the JDK HttpClient backend normalizes to lower-case
        findHeader(response.headers, "X-Req-Content-Type") shouldBe Some("application/json")
        findHeader(response.headers, "X-Req-Method") shouldBe Some("POST")
        findHeader(response.headers, "X-Req-X-Default") shouldBe Some("on")
        response.requestBodyString shouldBe """{"hello":"hi"}"""
      },
      onRequest(body = FormOf[Greeting]("hello" -> "world"))
        .respondsWith[String](StatusCode.Ok, description = "form request body")
        .assert { ctx =>
          val response = ctx.performRequest(defaultBackend)
          response.body shouldBe "hello=world"
          findHeader(response.headers, "X-Req-Content-Type") shouldBe Some("application/x-www-form-urlencoded")
        },
      onRequest(body = Multipart(TextPart("a", "b")))
        .respondsWith[String](StatusCode.Ok, description = "multipart request body")
        .assert { ctx =>
          val response = ctx.performRequest(defaultBackend)
          response.body should include("--baklava-multipart-boundary")
          findHeader(response.headers, "X-Req-Content-Type") shouldBe Some(SttpBodies.multipartContentType)
        },
      // core's "performRequest called exactly once" tracking only counts a call that returns, so a
      // decode failure (which throws before that point) can't be observed via intercept(ctx.performRequest(...))
      // without also tripping that check; decode as String (always succeeds) and invoke the adapter's own
      // decoding step directly on the same raw request/response to exercise the failure path instead
      onRequest(body = "not json")
        .respondsWith[String](StatusCode.Ok, description = "decode failure raises BaklavaAssertionException")
        .assert { ctx =>
          val response = ctx.performRequest(defaultBackend)
          val ex       = intercept[BaklavaAssertionException] {
            httpResponseToBaklavaResponseContext[Greeting](response.rawRequest, response.rawResponse)
          }
          ex.getMessage should include("Failed to decode response body")
        }
    )
  )

  path("/no-content")(
    supports(Method.GET, summary = "no content")(
      onRequest.respondsWith[EmptyBody](StatusCode.NoContent, description = "empty response body").assert { ctx =>
        ctx.performRequest(defaultBackend)
      }
    )
  )
}
