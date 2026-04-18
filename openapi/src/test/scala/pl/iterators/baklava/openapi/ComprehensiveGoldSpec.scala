package pl.iterators.baklava.openapi

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import io.circe.syntax.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpResponse}
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.stream.Materializer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.simple.BaklavaDslFormatterSimple
import pl.iterators.baklava.tsrest.BaklavaDslFormatterTsRest
import pl.iterators.baklava.{
  ApiKeyInHeader,
  BaklavaTestFrameworkDslDebug,
  EmptyBody,
  FilePart,
  FormOf,
  HttpBasic,
  HttpBearer,
  Multipart,
  OAuth2InBearer,
  OAuthAuthorizationCodeFlow,
  OAuthFlows,
  Schema,
  SchemaType,
  SecurityScheme,
  TextPart,
  ToQueryParam
}
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

/** Gold test for the three Baklava generators (OpenAPI, ts-rest, simple HTML).
  *
  * Builds a comprehensive but realistic API and drives it end-to-end through the Baklava DSL, then generates all three output formats from
  * the captured calls and compares byte-for-byte against checked-in golden files under `openapi/src/test/resources/gold/`.
  *
  * Run with `BAKLAVA_REGEN_GOLD=1` to overwrite the golden files when the generator output legitimately changes (review the diff before
  * committing). Gold files are cross-version — the test is expected to produce identical output on Scala 2.13 and Scala 3.
  */
class ComprehensiveGoldSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaPekkoHttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[Route, ToEntityMarshaller, FromEntityUnmarshaller]
    with BaklavaTestFrameworkDslDebug[Route, ToEntityMarshaller, FromEntityUnmarshaller, Unit, Unit, ScalatestAsExecution]
    with FailFastCirceSupport
    with KebsCirce
    with KebsCirceEnumsLowercase
    with KebsEnumeratum {

  private implicit val system: ActorSystem        = ActorSystem("comprehensive-gold")
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer         = Materializer(system)

  val routes: Route                                                 = complete(OK)
  override def strictHeaderCheckDefault: Boolean                    = false
  implicit val stringUnmarshaller: FromEntityUnmarshaller[String]   = Unmarshaller.stringUnmarshaller
  implicit val byteArrayMarshaller: ToEntityMarshaller[Array[Byte]] = PredefinedToEntityMarshallers.ByteArrayMarshaller

  // Stub: the test doesn't care about real HTTP — canned responses per assertion. We also
  // remember the last built request so tests can assert on what the adapter produced (used for
  // multipart / content-type overrides).
  private var nextResponse: HttpResponse                                            = HttpResponse(OK)
  private val lastRequest: java.util.concurrent.atomic.AtomicReference[HttpRequest] =
    new java.util.concurrent.atomic.AtomicReference[HttpRequest]()
  override def performRequest(routes: Route, request: HttpRequest): HttpResponse = {
    lastRequest.set(request)
    nextResponse
  }

  private def jsonResponse(status: org.apache.pekko.http.scaladsl.model.StatusCode, json: String): HttpResponse =
    HttpResponse(status, entity = HttpEntity(ContentTypes.`application/json`, json))
  private def textResponse(status: org.apache.pekko.http.scaladsl.model.StatusCode, text: String): HttpResponse =
    HttpResponse(status, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, text))
  private def emptyResponse(status: org.apache.pekko.http.scaladsl.model.StatusCode): HttpResponse = HttpResponse(status)
  private def jsonResponseWithHeaders(
      status: org.apache.pekko.http.scaladsl.model.StatusCode,
      json: String,
      headers: Seq[(String, String)]
  ): HttpResponse = {
    val hs: Seq[HttpHeader] = headers.map { case (n, v) => RawHeader(n, v) }
    HttpResponse(status, entity = HttpEntity(ContentTypes.`application/json`, json)).withHeaders(hs)
  }

  // Domain model + enums are defined in the companion object (ComprehensiveGoldSpec) below.
  // They must be top-level rather than nested in the test class so Scala 3's enumeratum
  // `findValues` macro can discover them, and so kebs can derive JSON codecs without warning.
  import ComprehensiveGoldSpec.*

  // ---------------------------------------------------------------------------
  // Security schemes
  // ---------------------------------------------------------------------------

  private val bearerAuth: HttpBearer = HttpBearer(bearerFormat = "JWT", description = "JWT token issued by /auth/login")
  private val bearerScheme           = SecurityScheme("bearerAuth", bearerAuth)

  private val basicAuth   = HttpBasic(description = "HTTP Basic for the login endpoint only")
  private val basicScheme = SecurityScheme("basicAuth", basicAuth)

  private val apiKey       = ApiKeyInHeader("X-API-Key", "API key for webhook delivery")
  private val apiKeyScheme = SecurityScheme("apiKey", apiKey)

  private val oauthAdmin = OAuth2InBearer(
    OAuthFlows(
      authorizationCodeFlow = Some(
        OAuthAuthorizationCodeFlow(
          authorizationUrl = "https://example.com/oauth/authorize",
          tokenUrl = "https://example.com/oauth/token",
          scopes = Map("projects:read" -> "Read projects", "projects:write" -> "Create/update projects")
        )
      )
    )
  )
  private val oauthScheme = SecurityScheme("oauth2", oauthAdmin)

  // ---------------------------------------------------------------------------
  // Fixture values
  // ---------------------------------------------------------------------------

  private val userId          = UUID.fromString("00000000-0000-0000-0000-000000000001")
  private val otherUser       = UUID.fromString("00000000-0000-0000-0000-000000000002")
  private val alice           = User(userId, "alice@example.com", "Alice", Role.Admin)
  private val bob             = User(otherUser, "bob@example.com", "Bob", Role.Member)
  private val project42       = Project(42L, "Apollo", Some("Mission control"), ProjectStatus.Active, userId, "2026-01-01T00:00:00Z")
  private val archivedProject =
    Project(7L, "Gemini", None, ProjectStatus.Archived, userId, "2025-06-01T00:00:00Z")
  private val task1 = Task(101L, "Wire up telemetry", Some("Prometheus + Grafana"), done = false, Priority.High)
  private val task2 = Task(102L, "Write docs", None, done = true, Priority.Low)

  // ---------------------------------------------------------------------------
  // API surface
  // ---------------------------------------------------------------------------

  path("/auth/login", description = "Authenticate against the API", summary = "Authentication")(
    supports(
      POST,
      securitySchemes = Seq(basicScheme),
      description = "Exchange HTTP Basic credentials for a JWT token",
      summary = "Login",
      operationId = "login",
      tags = Seq("Auth")
    )(
      onRequest(
        body = FormOf[LoginForm]("client_id" -> "web", "grant_type" -> "password"),
        security = basicAuth("alice@example.com", "s3cret")
      ).respondsWith[LoginResponse](OK, description = "Successful login")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, LoginResponse("jwt.token.xyz", alice).asJson.noSpaces)
          ctx.performRequest(routes)
        },
      onRequest(
        body = FormOf[LoginForm]("client_id" -> "web", "grant_type" -> "password"),
        security = basicAuth("mallory", "wrong")
      ).respondsWith[ErrorResponse](Unauthorized, description = "Invalid credentials")
        .assert { ctx =>
          nextResponse = jsonResponse(Unauthorized, ErrorResponse("unauthorized", "Bad credentials", None).asJson.noSpaces)
          ctx.performRequest(routes)
        }
    )
  )

  path("/me", description = "The authenticated caller", summary = "Current user")(
    supports(
      GET,
      securitySchemes = Seq(bearerScheme),
      description = "Return the profile of the currently authenticated user",
      summary = "Who am I",
      operationId = "me",
      tags = Seq("Auth")
    )(
      onRequest(security = bearerAuth("jwt.token.xyz"))
        .respondsWith[User](OK, description = "Authenticated user profile")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, alice.asJson.noSpaces)
          ctx.performRequest(routes)
        }
    )
  )

  path("/users", description = "User collection", summary = "Users")(
    supports(
      GET,
      queryParameters = (
        q[Option[Int]]("page", "Page number (1-indexed)"),
        q[Option[Int]]("limit", "Items per page (max 100)"),
        q[Option[Role]]("role", "Filter by role")
      ),
      securitySchemes = Seq(bearerScheme),
      description = "List users with pagination and optional role filter",
      summary = "List users",
      operationId = "listUsers",
      tags = Seq("Users")
    )(
      onRequest(queryParameters = (Some(1), Some(20), Some(Role.Admin)), security = bearerAuth("jwt.token.xyz"))
        .respondsWith[PaginatedUsers](
          OK,
          description = "First page of users",
          headers = Seq(h[Int]("X-Rate-Limit-Remaining", "Calls remaining in the current window"))
        )
        .assert { ctx =>
          nextResponse = jsonResponseWithHeaders(
            OK,
            PaginatedUsers(Seq(alice, bob), total = 2, page = 1, limit = 20).asJson.noSpaces,
            Seq("X-Rate-Limit-Remaining" -> "59")
          )
          ctx.performRequest(routes)
        }
    )
  )

  path("/users/{userId}", description = "Single user operations", summary = "User by ID")(
    supports(
      GET,
      pathParameters = p[UUID]("userId", "The user's UUID"),
      securitySchemes = Seq(bearerScheme),
      description = "Fetch a single user by UUID",
      summary = "Get user",
      operationId = "getUser",
      tags = Seq("Users")
    )(
      onRequest(pathParameters = userId, security = bearerAuth("jwt.token.xyz"))
        .respondsWith[User](OK, description = "User found")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, alice.asJson.noSpaces)
          ctx.performRequest(routes)
        },
      onRequest(pathParameters = UUID.fromString("00000000-0000-0000-0000-0000000000ff"), security = bearerAuth("jwt.token.xyz"))
        .respondsWith[ErrorResponse](NotFound, description = "User not found")
        .assert { ctx =>
          nextResponse = jsonResponse(NotFound, ErrorResponse("not_found", "No such user", None).asJson.noSpaces)
          ctx.performRequest(routes)
        }
    ),
    supports(
      PUT,
      pathParameters = p[UUID]("userId", "The user's UUID"),
      securitySchemes = Seq(bearerScheme),
      description = "Replace a user's profile (admin only)",
      summary = "Update user",
      operationId = "updateUser",
      tags = Seq("Users")
    )(
      onRequest(
        pathParameters = userId,
        body = UpdateUserRequest("Alice Updated", Role.Admin),
        security = bearerAuth("jwt.token.xyz")
      ).respondsWith[User](OK, description = "User updated")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, alice.copy(name = "Alice Updated").asJson.noSpaces)
          ctx.performRequest(routes)
        }
    ),
    supports(
      DELETE,
      pathParameters = p[UUID]("userId", "The user's UUID"),
      securitySchemes = Seq(bearerScheme),
      description = "Delete a user",
      summary = "Delete user",
      operationId = "deleteUser",
      tags = Seq("Users")
    )(
      onRequest(pathParameters = otherUser, security = bearerAuth("jwt.token.xyz"))
        .respondsWith[EmptyBody](NoContent, description = "User deleted")
        .assert { ctx =>
          nextResponse = emptyResponse(NoContent)
          ctx.performRequest(routes)
        }
    )
  )

  path("/projects", description = "Project collection", summary = "Projects")(
    supports(
      GET,
      queryParameters = q[Option[ProjectStatus]]("status", "Filter by lifecycle state"),
      securitySchemes = Seq(oauthScheme),
      description = "List projects, optionally filtered by status",
      summary = "List projects",
      operationId = "listProjects",
      tags = Seq("Projects")
    )(
      onRequest(queryParameters = Some(ProjectStatus.Active), security = oauthAdmin("oauth.access.token"))
        .respondsWith[Seq[Project]](OK, description = "All active projects")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, Seq(project42).asJson.noSpaces)
          ctx.performRequest(routes)
        },
      onRequest(queryParameters = Some(ProjectStatus.Archived), security = oauthAdmin("oauth.access.token"))
        .respondsWith[Seq[Project]](OK, description = "All archived projects")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, Seq(archivedProject).asJson.noSpaces)
          ctx.performRequest(routes)
        }
    ),
    supports(
      POST,
      securitySchemes = Seq(oauthScheme),
      description = "Create a new project",
      summary = "Create project",
      operationId = "createProject",
      tags = Seq("Projects")
    )(
      onRequest(
        body = CreateProjectRequest("Apollo", Some("Mission control"), ProjectStatus.Active),
        security = oauthAdmin("oauth.access.token")
      ).respondsWith[Project](Created, description = "Project created")
        .assert { ctx =>
          nextResponse = jsonResponse(Created, project42.asJson.noSpaces)
          ctx.performRequest(routes)
        },
      onRequest(
        body = CreateProjectRequest("", None, ProjectStatus.Draft),
        security = oauthAdmin("oauth.access.token")
      ).respondsWith[ErrorResponse](BadRequest, description = "Validation failed")
        .assert { ctx =>
          nextResponse = jsonResponse(
            BadRequest,
            ErrorResponse("validation", "One or more fields are invalid", Some(Seq("name: must not be blank"))).asJson.noSpaces
          )
          ctx.performRequest(routes)
        }
    )
  )

  path("/projects/{projectId}", description = "Project detail", summary = "Project by ID")(
    supports(
      PATCH,
      pathParameters = p[Long]("projectId", "The project ID"),
      securitySchemes = Seq(oauthScheme),
      description = "Partially update a project",
      summary = "Patch project",
      operationId = "patchProject",
      tags = Seq("Projects")
    )(
      onRequest(
        pathParameters = 42L,
        body = PatchProjectRequest(name = None, description = Some("Updated desc"), status = None),
        security = oauthAdmin("oauth.access.token")
      ).respondsWith[Project](OK, description = "Project updated")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, project42.copy(description = Some("Updated desc")).asJson.noSpaces)
          ctx.performRequest(routes)
        }
    )
  )

  path("/projects/{projectId}/tasks", description = "Tasks within a project", summary = "Project tasks")(
    supports(
      GET,
      pathParameters = p[Long]("projectId", "The project ID"),
      securitySchemes = Seq(oauthScheme),
      description = "List all tasks in a project",
      summary = "List tasks",
      operationId = "listTasks",
      tags = Seq("Tasks")
    )(
      onRequest(pathParameters = 42L, security = oauthAdmin("oauth.access.token"))
        .respondsWith[Seq[Task]](OK, description = "All tasks in the project")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, Seq(task1, task2).asJson.noSpaces)
          ctx.performRequest(routes)
        }
    ),
    supports(
      POST,
      pathParameters = p[Long]("projectId", "The project ID"),
      securitySchemes = Seq(oauthScheme),
      description = "Create a task in a project",
      summary = "Create task",
      operationId = "createTask",
      tags = Seq("Tasks")
    )(
      onRequest(
        pathParameters = 42L,
        body = CreateTaskRequest("New task", None, Priority.Medium),
        security = oauthAdmin("oauth.access.token")
      ).respondsWith[Task](
        Created,
        description = "Task created",
        headers = Seq(h[String]("Location", "URL of the newly created task"))
      ).assert { ctx =>
        nextResponse = jsonResponseWithHeaders(
          Created,
          task1.asJson.noSpaces,
          Seq("Location" -> "/projects/42/tasks/101")
        )
        ctx.performRequest(routes)
      }
    )
  )

  path("/webhooks", description = "Inbound webhooks", summary = "Webhooks")(
    supports(
      POST,
      securitySchemes = Seq(apiKeyScheme),
      description = "Accept a webhook payload",
      summary = "Deliver webhook",
      operationId = "deliverWebhook",
      tags = Seq("Webhooks")
    )(
      onRequest(body = WebhookPayload("order.created", """{"id":1}"""), security = apiKey("secret-key"))
        .respondsWith[WebhookAck](Accepted, description = "Webhook accepted")
        .assert { ctx =>
          nextResponse = jsonResponse(Accepted, WebhookAck(received = true).asJson.noSpaces)
          ctx.performRequest(routes)
        },
      onRequest(body = WebhookPayload("order.created", """{"id":1}"""), security = apiKey("secret-key"))
        .respondsWith[String](Accepted, description = "Legacy plain-text ack")
        .assert { ctx =>
          nextResponse = textResponse(Accepted, "ACK")
          ctx.performRequest(routes)
        }
    )
  )

  path("/health", description = "Liveness probe", summary = "Health")(
    supports(
      GET,
      description = "Return service liveness — no authentication required",
      summary = "Liveness probe",
      operationId = "health",
      tags = Seq("System")
    )(
      onRequest()
        .respondsWith[HealthResponse](OK, description = "Service is alive")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, HealthResponse("ok", 12345L).asJson.noSpaces)
          ctx.performRequest(routes)
        }
    )
  )

  // Multipart upload — file part + text part in the same request body. The generator captures
  // `Content-Type: multipart/form-data; boundary=…` from the wire request, so the OpenAPI spec
  // emits `requestBody.content["multipart/form-data"]`. Covers issue #81.
  path("/users/{userId}/photo", description = "Upload a profile photo with a caption", summary = "Photo")(
    supports(
      POST,
      pathParameters = p[UUID]("userId", "The user's UUID"),
      securitySchemes = Seq(bearerScheme),
      description = "Upload a profile photo alongside a caption as multipart/form-data",
      summary = "Upload photo",
      operationId = "uploadPhoto",
      tags = Seq("Users")
    )(
      onRequest(
        pathParameters = userId,
        body = Multipart(
          FilePart("photo", "image/png", "photo.png", "fake png bytes".getBytes(StandardCharsets.UTF_8)),
          TextPart("caption", "profile photo")
        ),
        security = bearerAuth("jwt.token.xyz")
      ).respondsWith[EmptyBody](NoContent, description = "Photo uploaded")
        .assert { ctx =>
          nextResponse = emptyResponse(NoContent)
          val response = ctx.performRequest(routes)
          // The request that hit `performRequest` must carry a multipart/form-data Content-Type
          // — proves the adapter mapped Baklava's `Multipart` to pekko's native multipart form.
          // The content type includes a boundary parameter, so compare the mediaType prefix.
          lastRequest.get().entity.contentType.mediaType.value should startWith("multipart/form-data")
          response.status.intValue() shouldBe 204
        }
    )
  )

  // ---------------------------------------------------------------------------
  // Gold comparison
  // ---------------------------------------------------------------------------

  private val regen: Boolean = sys.env.get("BAKLAVA_REGEN_GOLD").exists(v => v == "1" || v.equalsIgnoreCase("true"))

  override def afterAll(): Unit = {
    // Route through the public formatter entry points the sbt plugin uses in production,
    // with the same config keys (`openapi-info`, `ts-rest-package-contract-json`). That way the
    // gold files reflect exactly what end users see, including the top-level `info:` block that
    // comes from openapi-info.
    val config = Map(
      "openapi-info"                  -> openApiInfo,
      "ts-rest-package-contract-json" -> tsRestPackageJson
    )

    // 1. OpenAPI YAML
    val openapiDir = new File("target/baklava/openapi")
    deleteRecursively(openapiDir)
    new BaklavaDslFormatterOpenAPI().create(config, listCalls)
    assertGoldDir("openapi", openapiDir)

    // 2. Simple HTML
    val simpleDir = new File("target/baklava/simple")
    deleteRecursively(simpleDir)
    new BaklavaDslFormatterSimple().create(config, listCalls)
    assertGoldDir("simple", simpleDir)

    // 3. ts-rest
    val tsrestDir = new File("target/baklava/tsrest")
    deleteRecursively(tsrestDir)
    new BaklavaDslFormatterTsRest().create(config, listCalls)
    assertGoldDir("tsrest", tsrestDir)

    if (regen) println(s"[gold] Regenerated gold files under ${goldRoot.getAbsolutePath}")
    val _ = system.terminate()
    super.afterAll()
  }

  private val openApiInfo =
    """{
      |  "openapi": "3.0.1",
      |  "info": {
      |    "title": "Baklava Comprehensive Gold Spec",
      |    "description": "Fixture API used by the gold test to exercise all three generators.",
      |    "version": "0.0.0-test"
      |  }
      |}
      |""".stripMargin

  private val tsRestPackageJson =
    """{
      |  "name": "baklava-gold-contracts",
      |  "version": "0.0.0-test",
      |  "main": "index.js",
      |  "types": "index.d.ts"
      |}
      |""".stripMargin

  // Gold files live at openapi/src/test/resources/gold/<format>/… — stored under the module's
  // resources so they're on the test classpath and travel with the repo. sbt runs tests with the
  // repository root as CWD (not the subproject), so the regen write path is prefixed accordingly.
  private val goldRoot = new File("openapi/src/test/resources/gold")

  private def assertGoldDir(format: String, actualDir: File): Unit = {
    val actualFiles   = listRelativeFiles(actualDir).sorted
    val goldFormatDir = new File(goldRoot, format)
    if (regen) {
      // Wipe this format's gold dir so deletions are reflected.
      deleteRecursively(goldFormatDir)
      goldFormatDir.mkdirs()
      actualFiles.foreach { rel =>
        val target = new File(goldFormatDir, rel)
        target.getParentFile.mkdirs()
        Files.copy(new File(actualDir, rel).toPath, target.toPath)
      }
    } else {
      assert(goldFormatDir.exists(), s"Gold dir missing: ${goldFormatDir.getAbsolutePath} — run with BAKLAVA_REGEN_GOLD=1 to create")
      val goldFiles  = listRelativeFiles(goldFormatDir).sorted
      val onlyActual = actualFiles.diff(goldFiles)
      val onlyGold   = goldFiles.diff(actualFiles)
      assert(
        onlyActual.isEmpty && onlyGold.isEmpty,
        s"File-set mismatch in $format.\n" +
          s"  in actual but not gold: ${onlyActual.mkString(", ")}\n" +
          s"  in gold but not actual: ${onlyGold.mkString(", ")}\n" +
          "  Run with BAKLAVA_REGEN_GOLD=1 to update."
      )
      actualFiles.foreach { rel =>
        val actual   = new String(Files.readAllBytes(new File(actualDir, rel).toPath), StandardCharsets.UTF_8)
        val expected = new String(Files.readAllBytes(new File(goldFormatDir, rel).toPath), StandardCharsets.UTF_8)
        if (actual != expected) {
          fail(
            s"Gold mismatch for $format/$rel (run with BAKLAVA_REGEN_GOLD=1 to update after reviewing).\n" +
              diffSummary(expected, actual)
          )
        }
      }
    }
  }

  private def listRelativeFiles(root: File): Seq[String] = {
    if (!root.exists()) Seq.empty
    else {
      val rootPath: Path = root.toPath
      Files.walk(rootPath).iterator().asScala.toSeq.filter(Files.isRegularFile(_)).map(p => rootPath.relativize(p).toString)
    }
  }

  private def deleteRecursively(f: File): Unit = {
    if (f.exists()) {
      if (f.isDirectory) Option(f.listFiles()).toSeq.flatten.foreach(deleteRecursively)
      val _ = f.delete()
    }
  }

  // Minimal text diff for failure messages — first differing line window.
  private def diffSummary(expected: String, actual: String): String = {
    val e           = expected.split('\n').toIndexedSeq
    val a           = actual.split('\n').toIndexedSeq
    val i           = (0 until math.min(e.length, a.length)).find(idx => e(idx) != a(idx)).getOrElse(math.min(e.length, a.length))
    val windowStart = math.max(0, i - 2)
    val windowEnd   = math.min(math.max(e.length, a.length), i + 5)
    val eWindow     = (windowStart until math.min(e.length, windowEnd)).map(idx => f"  gold   $idx%4d: ${e(idx)}").mkString("\n")
    val aWindow     = (windowStart until math.min(a.length, windowEnd)).map(idx => f"  actual $idx%4d: ${a(idx)}").mkString("\n")
    s"first diff at line $i (gold=${e.length} lines, actual=${a.length} lines)\n$eWindow\n---\n$aWindow"
  }

  // Force ScalaTest to see a no-op assertion at test-discovery time so the spec isn't reported
  // as empty. (All actual work happens via `path(...)` call-site registration + `afterAll`.)
  describe("comprehensive gold test") {
    it("is a placeholder — the real work is done in afterAll against captured calls")(succeed)
  }
}

object ComprehensiveGoldSpec {

  sealed trait Role extends EnumEntry with Lowercase
  object Role       extends Enum[Role] {
    case object Admin  extends Role
    case object Member extends Role
    case object Guest  extends Role
    val values: IndexedSeq[Role]                  = findValues
    implicit val schema: Schema[Role]             = enumSchema[Role]("Role", "User role within the system", values.map(_.entryName))
    implicit val toQueryParam: ToQueryParam[Role] = new ToQueryParam[Role] {
      def apply(t: Role): Seq[String] = Seq(t.entryName)
    }
  }

  sealed trait ProjectStatus extends EnumEntry with Lowercase
  object ProjectStatus       extends Enum[ProjectStatus] {
    case object Active   extends ProjectStatus
    case object Archived extends ProjectStatus
    case object Draft    extends ProjectStatus
    val values: IndexedSeq[ProjectStatus]      = findValues
    implicit val schema: Schema[ProjectStatus] =
      enumSchema[ProjectStatus]("ProjectStatus", "Lifecycle state of a project", values.map(_.entryName))
    implicit val toQueryParam: ToQueryParam[ProjectStatus] = new ToQueryParam[ProjectStatus] {
      def apply(t: ProjectStatus): Seq[String] = Seq(t.entryName)
    }
  }

  sealed trait Priority extends EnumEntry with Lowercase
  object Priority       extends Enum[Priority] {
    case object Low    extends Priority
    case object Medium extends Priority
    case object High   extends Priority
    val values: IndexedSeq[Priority]      = findValues
    implicit val schema: Schema[Priority] = enumSchema[Priority]("Priority", "Task priority level", values.map(_.entryName))
  }

  private def enumSchema[T](name: String, desc: String, values: Seq[String]): Schema[T] = new Schema[T] {
    val `type`: SchemaType                 = SchemaType.StringType
    val className: String                  = name
    val format: Option[String]             = None
    val properties: Map[String, Schema[?]] = Map.empty
    val items: Option[Schema[?]]           = None
    val `enum`: Option[Set[String]]        = Some(values.toSet)
    val required: Boolean                  = true
    val additionalProperties: Boolean      = false
    val default: Option[T]                 = None
    val description: Option[String]        = Some(desc)
  }

  case class User(id: UUID, email: String, name: String, role: Role)
  case class PaginatedUsers(users: Seq[User], total: Int, page: Int, limit: Int)
  case class UpdateUserRequest(name: String, role: Role)

  case class Project(
      id: Long,
      name: String,
      description: Option[String],
      status: ProjectStatus,
      ownerId: UUID,
      createdAt: String
  )
  case class CreateProjectRequest(name: String, description: Option[String], status: ProjectStatus)
  case class PatchProjectRequest(name: Option[String], description: Option[String], status: Option[ProjectStatus])

  case class Task(id: Long, title: String, description: Option[String], done: Boolean, priority: Priority)
  case class CreateTaskRequest(title: String, description: Option[String], priority: Priority)

  case class LoginForm(client_id: String, grant_type: String)
  case class LoginResponse(token: String, user: User)
  case class ErrorResponse(code: String, message: String, details: Option[Seq[String]])
  case class WebhookPayload(event: String, data: String)
  case class WebhookAck(received: Boolean)
  case class HealthResponse(status: String, uptimeSeconds: Long)
}
