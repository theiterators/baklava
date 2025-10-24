---
sidebar_position: 5
title: DSL Reference
---

# DSL Reference

The Baklava DSL provides a comprehensive, tree-structured API for documenting and testing HTTP endpoints. This reference covers all DSL elements and their attributes.

## DSL Tree Structure

The Baklava DSL follows a hierarchical tree structure:

```
path (root)
  └── supports (HTTP method)
        └── onRequest (test case)
              ├── respondsWith (expected response)
              └── assert (test assertions)
```

Each level in the tree defines specific aspects of your API endpoint and its behavior.

## Complete Example

Here's a comprehensive example demonstrating all major DSL features:

```scala
path(
  path = "/users/{userId}",
  description = "User management endpoints",
  summary = "Manage user resources"
)(
  supports(
    method = GET,
    pathParameters = p[Long]("userId", "The unique user identifier"),
    queryParameters = (
      q[Option[String]]("fields", "Comma-separated list of fields to include"),
      q[Option[Int]]("version", "API version number")
    ),
    headers = h[String]("X-Request-ID", "Unique request identifier"),
    securitySchemes = Seq(
      SecurityScheme("bearerAuth", HttpBearer())
    ),
    description = "Retrieve a user by ID",
    summary = "Get user",
    operationId = "getUser",
    tags = List("Users", "Read Operations")
  )(
    onRequest(
      pathParameters = (1L),
      queryParameters = (Some("name,email"), None),
      headers = ("req-123"),
      security = HttpBearer()("my-token")
    )
      .respondsWith[User](
        statusCode = OK,
        headers = Seq(
          h[String]("X-Response-ID", "Response identifier"),
          h[String]("X-Rate-Limit", "Rate limit information")
        ),
        description = "Successfully retrieved user",
        strictHeaderCheck = false
      )
      .assert { ctx =>
        val response = ctx.performRequest(allRoutes)

        response.body.id shouldBe 1L
        response.body.name should not be empty
      },

    onRequest(
      pathParameters = (999L)
    )
      .respondsWith[ErrorResponse](
        statusCode = NotFound,
        description = "User not found"
      )
      .assert { ctx =>
        val response = ctx.performRequest(allRoutes)

        response.body.code shouldBe "USER_NOT_FOUND"
      }
  ),

  supports(
    method = POST,
    description = "Create a new user",
    summary = "Create user",
    tags = List("Users", "Write Operations")
  )(
    onRequest(
      body = CreateUserRequest("John Doe", "john@example.com")
    )
      .respondsWith[User](
        statusCode = Created,
        description = "User created successfully"
      )
      .assert { ctx =>
        val response = ctx.performRequest(allRoutes)

        response.body.name shouldBe "John Doe"
        response.body.email shouldBe "john@example.com"
      }
  )
)
```

## Root Element: `path`

The [`path()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaTestFrameworkDsl.scala#L21) function is the root element of the DSL tree. It defines an API endpoint path and contains one or more HTTP method definitions.

### Signature

```scala
def path(
  path: String,
  description: String = "",
  summary: String = ""
)(
  steps: BaklavaMethodDefinition*
): TestFrameworkFragmentsType
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | `String` | Yes | The URL path pattern, supporting path parameters in `{paramName}` format (e.g., `/users/{userId}`) |
| `description` | `String` | No | Detailed description of the path and its purpose |
| `summary` | `String` | No | Brief summary of the path |
| `steps` | `BaklavaMethodDefinition*` | Yes | One or more [`supports()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaTestFrameworkDsl.scala#L63) definitions for HTTP methods |

### Example

```scala
path(
  path = "/api/v1/products/{productId}",
  description = "Product resource endpoints for CRUD operations",
  summary = "Product management"
)(
  supports(GET, ...),
  supports(PUT, ...),
  supports(DELETE, ...)
)
```

## HTTP Method Element: `supports`

The [`supports()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaTestFrameworkDsl.scala#L63) function defines an HTTP method operation on a path. It specifies the method, parameters, security, and contains test cases.

### Signature

```scala
def supports[PathParameters, QueryParameters, Headers](
  method: BaklavaHttpMethod,
  securitySchemes: Seq[SecurityScheme] = Seq.empty,
  pathParameters: PathParameters = (),
  queryParameters: QueryParameters = (),
  headers: Headers = (),
  description: String = "",
  summary: String = "",
  operationId: String = "",
  tags: Seq[String] = Seq.empty
)(
  steps: BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers]*
): BaklavaMethodDefinition
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `method` | `BaklavaHttpMethod` | Yes | HTTP method (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS) |
| `securitySchemes` | `Seq[SecurityScheme]` | No | Security schemes available for this operation (see [Security](#security)) |
| `pathParameters` | `PathParameters` | No | Path parameter definitions using [`p[T]()`](#path-parameters) |
| `queryParameters` | `QueryParameters` | No | Query parameter definitions using [`q[T]()`](#query-parameters) |
| `headers` | `Headers` | No | Header definitions using [`h[T]()`](#headers) |
| `description` | `String` | No | Detailed operation description |
| `summary` | `String` | No | Brief operation summary |
| `operationId` | `String` | No | Unique identifier for the operation |
| `tags` | `Seq[String]` | No | Tags for grouping operations in documentation |
| `steps` | `BaklavaIntermediateTestCase*` | Yes | One or more test cases using [`onRequest()`](#test-case-element-onrequest) |

### Example

```scala
supports(
  method = GET,
  pathParameters = p[String]("userId", "User identifier"),
  queryParameters = (
    q[Option[Int]]("limit", "Maximum number of results"),
    q[Option[Int]]("offset", "Pagination offset")
  ),
  headers = h[String]("X-API-Key", "API authentication key"),
  securitySchemes = Seq(
    SecurityScheme("apiKey", ApiKeyInHeader("X-API-Key"))
  ),
  description = "Retrieve user details with optional pagination",
  summary = "Get user",
  operationId = "getUserById",
  tags = List("Users", "Public API")
)(
  // test cases...
)
```

## Test Case Element: `onRequest`

The [`onRequest()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaTestFrameworkDsl.scala#L377) function defines a specific test case for an HTTP operation. It specifies the request parameters and must be followed by [`respondsWith()`](#response-definition-respondswith) and [`assert()`](#assertion-block-assert).

### Signature

```scala
def onRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided](
  body: RequestBody = EmptyBodyInstance,
  security: AppliedSecurity = AppliedSecurity(NoopSecurity, Map.empty),
  pathParameters: PathParametersProvided = (),
  queryParameters: QueryParametersProvided = (),
  headers: HeadersProvided = ()
): OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `body` | `RequestBody` | No | Request body (defaults to empty). Can be a case class, `FormOf[T]` for form data, or `EmptyBodyInstance` |
| `security` | `AppliedSecurity` | No | Applied security credentials (see [Security](#security)) |
| `pathParameters` | `PathParametersProvided` | No | Actual path parameter values matching the definition |
| `queryParameters` | `QueryParametersProvided` | No | Actual query parameter values matching the definition |
| `headers` | `HeadersProvided` | No | Actual header values matching the definition |

### Example

```scala
onRequest(
  body = CreateUserRequest("Alice", "alice@example.com"),
  pathParameters = (123L),
  queryParameters = (Some(10), Some(0)),
  headers = ("api-key-value"),
  security = HttpBearer()("my-jwt-token")
)
```

## Response Definition: `respondsWith`

The [`respondsWith()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaTestFrameworkDsl.scala#L152) method defines the expected HTTP response for a test case.

### Signature

```scala
def respondsWith[ResponseBody](
  statusCode: BaklavaHttpStatus,
  headers: Seq[Header[?]] = Seq.empty,
  description: String = "",
  strictHeaderCheck: Boolean = strictHeaderCheckDefault
): BaklavaTestCase[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `statusCode` | `BaklavaHttpStatus` | Yes | Expected HTTP status code (OK, Created, NotFound, etc.) |
| `headers` | `Seq[Header[?]]` | No | Expected response headers using [`h[T]()`](#headers) |
| `description` | `String` | No | Description of this response scenario |
| `strictHeaderCheck` | `Boolean` | No | If true, response must contain exactly the specified headers (no more, no less) |

### Type Parameter

- `ResponseBody`: The expected response body type. Use `EmptyBody` for responses without a body.

### Example

```scala
.respondsWith[User](
  statusCode = OK,
  headers = Seq(
    h[String]("X-Request-ID"),
    h[Int]("X-Rate-Limit-Remaining")
  ),
  description = "User found and returned successfully",
  strictHeaderCheck = false
)
```

## Assertion Block: `assert`

The [`assert()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaTestFrameworkDsl.scala#L123) method contains the test framework-specific assertions. It receives a context object that provides access to the request/response.

### Signature

```scala
def assert[R](
  r: BaklavaCaseContext[...] => R
): BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers]
```

### Context Object

The assertion block receives a [`BaklavaCaseContext`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaHttpDsl.scala#L110) with:

- `ctx`: The request context containing all request parameters
- `performRequest(route)`: Method to execute the HTTP request against your routes

### Example

```scala
.assert { ctx =>
  // Execute the request
  val response = ctx.performRequest(allRoutes)

  // Test framework-specific assertions
  response.body.id shouldBe 1L
  response.body.name shouldBe "Alice"
  response.body.email should include("@example.com")

  // Access response metadata
  response.status shouldBe OK
  response.headers.headers should contain key "X-Request-ID"
}
```

## Parameters

### Path Parameters

Path parameters are defined using the [`p[T]()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaPathParams.scala#L27) function and represent dynamic segments in the URL path.

#### Signature

```scala
def p[T](
  name: String,
  description: String = ""
)(implicit tsm: ToPathParam[T], schema: Schema[T]): PathParam[T]
```

#### Supported Types

- Primitives: `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`, `Char`
- `BigDecimal`
- `java.util.UUID`

#### Examples

```scala
// Single path parameter
pathParameters = p[Long]("userId", "The user's unique identifier")

// Multiple path parameters (as tuple)
pathParameters = (
  p[String]("organizationId", "Organization identifier"),
  p[Long]("userId", "User identifier")
)

// Providing values in onRequest
onRequest(pathParameters = (123L))
onRequest(pathParameters = ("org-123", 456L))
```

### Query Parameters

Query parameters are defined using the [`q[T]()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaQueryParams.scala#L24) function and represent URL query string parameters.

#### Signature

```scala
def q[T](
  name: String,
  description: String = ""
)(implicit tsm: ToQueryParam[T], schema: Schema[T]): QueryParam[T]
```

#### Supported Types

- All path parameter types
- `Option[T]` for optional parameters
- `Seq[T]` for multi-value parameters

#### Examples

```scala
// Single query parameter
queryParameters = q[String]("search", "Search term")

// Multiple query parameters
queryParameters = (
  q[Option[Int]]("limit", "Maximum results"),
  q[Option[Int]]("offset", "Pagination offset"),
  q[Option[String]]("sort", "Sort field")
)

// Multi-value parameter
queryParameters = q[Seq[String]]("tags", "Filter by tags")

// Providing values in onRequest
onRequest(queryParameters = ("search term"))
onRequest(queryParameters = (Some(10), Some(0), None))
onRequest(queryParameters = (Seq("scala", "api")))
```

### Headers

Headers are defined using the [`h[T]()`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaHeaders.scala#L26) function and represent HTTP headers.

#### Signature

```scala
def h[T](
  name: String,
  description: String = ""
)(implicit tsm: ToHeader[T], schema: Schema[T]): Header[T]
```

#### Supported Types

- All query parameter types
- `Option[T]` for optional headers

#### Examples

```scala
// Single header
headers = h[String]("X-API-Key", "API authentication key")

// Multiple headers
headers = (
  h[String]("X-Request-ID", "Request tracking ID"),
  h[Option[String]]("X-Correlation-ID", "Optional correlation ID"),
  h[Int]("X-API-Version", "API version number")
)

// Providing values in onRequest
onRequest(headers = ("my-api-key"))
onRequest(headers = ("req-123", Some("corr-456"), 2))
```

## Request Body

Request bodies can be specified in several ways:

### Case Class Body

```scala
case class CreateUserRequest(name: String, email: String)

onRequest(
  body = CreateUserRequest("Alice", "alice@example.com")
)
```

### Form Data

Use [`FormOf[T]`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/BaklavaHttpDsl.scala#L17) for `application/x-www-form-urlencoded` data:

```scala
case class LoginForm(username: String, password: String)

onRequest(
  body = FormOf[LoginForm](
    "username" -> "alice",
    "password" -> "secret123"
  )
)
```

### Empty Body

For requests without a body (like GET requests):

```scala
onRequest()  // body defaults to EmptyBodyInstance
```

## Security

Baklava supports various security schemes defined in the [OpenAPI specification](https://swagger.io/specification/#security-scheme-object).

### Defining Security Schemes

Security schemes are defined in the [`supports()`](#http-method-element-supports) method:

```scala
supports(
  method = GET,
  securitySchemes = Seq(
    SecurityScheme("bearerAuth", HttpBearer()),
    SecurityScheme("apiKey", ApiKeyInHeader("X-API-Key"))
  ),
  // ...
)
```

### Applying Security in Tests

Security credentials are provided in [`onRequest()`](#test-case-element-onrequest):

```scala
// HTTP Bearer
onRequest(
  security = HttpBearer()("my-jwt-token")
)

// HTTP Basic
onRequest(
  security = HttpBasic()("username", "password")
)

// API Key in Header
onRequest(
  security = ApiKeyInHeader("X-API-Key")("my-api-key")
)

// API Key in Query
onRequest(
  security = ApiKeyInQuery("api_key")("my-api-key")
)

// API Key in Cookie
onRequest(
  security = ApiKeyInCookie("session")("session-token")
)

// OAuth2 Bearer
onRequest(
  security = OAuth2InBearer(flows)("access-token")
)

// OpenID Connect
onRequest(
  security = OpenIdConnectInBearer(url)("id-token")
)

// Mutual TLS
onRequest(
  security = MutualTls()()
)
```

### Available Security Types

All security types are defined in [`Security.scala`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L1):

- [`HttpBearer`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L20): Bearer token authentication
- [`HttpBasic`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L27): Basic authentication
- [`ApiKeyInHeader`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L36): API key in header
- [`ApiKeyInQuery`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L42): API key in query parameter
- [`ApiKeyInCookie`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L48): API key in cookie
- [`MutualTls`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L54): Mutual TLS authentication
- [`OpenIdConnectInBearer`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L60): OpenID Connect in bearer token
- [`OpenIdConnectInCookie`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L66): OpenID Connect in cookie
- [`OAuth2InBearer`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L72): OAuth2 in bearer token
- [`OAuth2InCookie`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Security.scala#L78): OAuth2 in cookie

## HTTP Methods

Baklava supports all standard HTTP methods:

- `GET`: Retrieve resources
- `POST`: Create resources
- `PUT`: Update/replace resources
- `PATCH`: Partially update resources
- `DELETE`: Delete resources
- `HEAD`: Retrieve headers only
- `OPTIONS`: Retrieve supported methods

## HTTP Status Codes

Common status codes available:

- **2xx Success**: `OK` (200), `Created` (201), `Accepted` (202), `NoContent` (204)
- **3xx Redirection**: `MovedPermanently` (301), `Found` (302), `NotModified` (304)
- **4xx Client Errors**: `BadRequest` (400), `Unauthorized` (401), `Forbidden` (403), `NotFound` (404), `Conflict` (409), `UnprocessableEntity` (422)
- **5xx Server Errors**: `InternalServerError` (500), `NotImplemented` (501), `ServiceUnavailable` (503)

## Schema System

Baklava uses a [`Schema[T]`](https://github.com/theiterators/baklava/blob/v1.0.8/core/src/main/scala/pl/iterators/baklava/Schema.scala#L20) type class to describe data types for documentation generation. Schemas are automatically derived for:

- Primitive types (String, Int, Long, Boolean, etc.)
- Collections (Seq, List, Map)
- Option types
- Case classes (using automatic derivation)

### Custom Schemas

You can define custom schemas for your types:

```scala
implicit val myTypeSchema: Schema[MyType] = Schema.derived[MyType]
  .withDescription("Description of MyType")
  .withDefault(MyType.default)
```

## Best Practices

1. **Use descriptive names**: Provide clear `description` and `summary` fields for better documentation
2. **Test multiple scenarios**: Include both success and error cases in your tests
3. **Leverage type safety**: Use specific types for parameters rather than generic `String`
4. **Document security**: Always specify `securitySchemes` when endpoints require authentication
5. **Use strict header checks sparingly**: Set `strictHeaderCheck = false` unless you need exact header matching
6. **Organize by resource**: Group related paths together in your test files

## Common Patterns

### Testing Pagination

```scala
supports(
  method = GET,
  queryParameters = (
    q[Option[Int]]("limit"),
    q[Option[Int]]("offset")
  )
)(
  onRequest(queryParameters = (Some(10), Some(0)))
    .respondsWith[PagedResponse[User]](OK)
    .assert { ctx =>
      val response = ctx.performRequest(allRoutes)
      response.body.items should have size 10
      response.body.total should be > 10
    }
)
```

### Testing Error Responses

```scala
onRequest(pathParameters = (999L))
  .respondsWith[ErrorResponse](
    statusCode = NotFound,
    description = "User not found"
  )
  .assert { ctx =>
    val response = ctx.performRequest(allRoutes)
    response.body.code shouldBe "USER_NOT_FOUND"
    response.body.message should include("999")
  }
```

### Testing Form Submissions

```scala
onRequest(
  body = FormOf[LoginForm](
    "username" -> "alice",
    "password" -> "secret"
  )
)
  .respondsWith[AuthToken](OK)
  .assert { ctx =>
    val response = ctx.performRequest(allRoutes)
    response.body.token should not be empty
  }
```

## See Also

- [Installation Guide](installation.md) - Setting up Baklava
- [Output Formats](output-formats.md) - Generating documentation
- [Configuration](configuration.md) - Configuring Baklava
