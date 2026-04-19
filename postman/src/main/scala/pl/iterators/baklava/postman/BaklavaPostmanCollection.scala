package pl.iterators.baklava.postman

import io.circe.Json
import pl.iterators.baklava.*

/** Pure conversion from the captured `BaklavaSerializableCall` list to a Postman Collection v2.1.0 JSON document. No I/O. */
private[postman] object BaklavaPostmanCollection {

  private val schemaUrl = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"

  def build(collectionName: String, calls: Seq[BaklavaSerializableCall]): Json = {
    val (folders, uncategorized) = groupByPrimaryTag(calls)

    val folderItems = folders.toSeq.sortBy(_._1).map { case (tag, tagCalls) =>
      folderNode(tag, groupedEndpointItems(tagCalls))
    }
    val topLevelItems = groupedEndpointItems(uncategorized)

    Json.obj(
      "info" -> Json.obj(
        "name"   -> Json.fromString(collectionName),
        "schema" -> Json.fromString(schemaUrl)
      ),
      "item"     -> Json.arr(folderItems ++ topLevelItems: _*),
      "variable" -> Json.arr(collectionVariables(calls): _*)
    )
  }

  /** Partition calls by their first `operationTag`. Calls with no tag go into the top-level `item` array; tagged calls become folders. */
  private def groupByPrimaryTag(
      calls: Seq[BaklavaSerializableCall]
  ): (Map[String, Seq[BaklavaSerializableCall]], Seq[BaklavaSerializableCall]) = {
    val byTag = calls
      .flatMap(c => c.request.operationTags.headOption.map(_ -> c))
      .groupMap(_._1)(_._2)
    val untagged = calls.filter(_.request.operationTags.isEmpty)
    (byTag, untagged)
  }

  private def groupedEndpointItems(calls: Seq[BaklavaSerializableCall]): Seq[Json] = {
    calls
      .groupBy(c => (c.request.method.map(_.method).getOrElse(""), c.request.symbolicPath))
      .toSeq
      .sortBy { case ((m, p), _) => (p, m) }
      .map { case (_, endpointCalls) => endpointItem(endpointCalls.sortBy(_.response.status.code)) }
  }

  private def folderNode(name: String, items: Seq[Json]): Json =
    Json.obj(
      "name" -> Json.fromString(name),
      "item" -> Json.arr(items: _*)
    )

  private def endpointItem(calls: Seq[BaklavaSerializableCall]): Json = {
    val primary    = calls.head
    val req        = primary.request
    val methodName = req.method.map(_.method).getOrElse("GET").toUpperCase

    val displayName =
      req.operationSummary
        .orElse(req.operationId)
        .getOrElse(s"$methodName ${req.symbolicPath}")

    val description = req.operationDescription

    val requestJson = Json.obj(
      "method"      -> Json.fromString(methodName),
      "header"      -> headerArray(req),
      "url"         -> urlObject(primary),
      "body"        -> requestBody(primary),
      "auth"        -> requestAuth(req),
      "description" -> description.map(Json.fromString).getOrElse(Json.Null)
    )

    Json.obj(
      "name"     -> Json.fromString(displayName),
      "request"  -> requestJson,
      "response" -> Json.arr(calls.map(responseExample): _*)
    )
  }

  /** Translate `/users/{userId}` → Postman's `/users/:userId` plus variable definitions derived from captured path param examples. */
  private def urlObject(call: BaklavaSerializableCall): Json = {
    val symbolic    = call.request.symbolicPath
    val postmanPath = symbolic
      .split('/')
      .filter(_.nonEmpty)
      .map { segment =>
        if (segment.startsWith("{") && segment.endsWith("}")) ":" + segment.substring(1, segment.length - 1)
        else segment
      }
      .toSeq

    val rawBase   = "{{baseUrl}}"
    val rawPath   = postmanPath.mkString("/")
    val queryJson = call.request.queryParametersSeq.sortBy(_.name).map { q =>
      Json.obj(
        "key"         -> Json.fromString(q.name),
        "value"       -> q.example.map(Json.fromString).getOrElse(Json.fromString("")),
        "description" -> q.description.map(Json.fromString).getOrElse(Json.Null)
      )
    }
    val rawQuery =
      if (queryJson.isEmpty) ""
      else "?" + call.request.queryParametersSeq.sortBy(_.name).map(q => s"${q.name}=${q.example.getOrElse("")}").mkString("&")

    val variables = call.request.pathParametersSeq.map { p =>
      Json.obj(
        "key"         -> Json.fromString(p.name),
        "value"       -> p.example.map(Json.fromString).getOrElse(Json.fromString("")),
        "description" -> p.description.map(Json.fromString).getOrElse(Json.Null)
      )
    }

    Json.obj(
      "raw"      -> Json.fromString(s"$rawBase/$rawPath$rawQuery"),
      "host"     -> Json.arr(Json.fromString(rawBase)),
      "path"     -> Json.arr(postmanPath.map(Json.fromString): _*),
      "query"    -> (if (queryJson.isEmpty) Json.Null else Json.arr(queryJson: _*)),
      "variable" -> (if (variables.isEmpty) Json.Null else Json.arr(variables: _*))
    )
  }

  /** Request headers: declared ones with their captured example values. Skip `Authorization`/`Content-Type` — Postman derives those from
    * `auth` and `body.options.raw.language` respectively, and leaving them in `header[]` produces duplicates on the wire.
    */
  private def headerArray(req: BaklavaRequestContextSerializable): Json = {
    val suppressed = Set("authorization", "content-type")
    val entries    = req.headersSeq
      .filterNot(h => suppressed.contains(h.name.toLowerCase))
      .sortBy(_.name)
      .map { h =>
        Json.obj(
          "key"         -> Json.fromString(h.name),
          "value"       -> h.example.map(Json.fromString).getOrElse(Json.fromString("")),
          "description" -> h.description.map(Json.fromString).getOrElse(Json.Null)
        )
      }
    Json.arr(entries: _*)
  }

  private def requestBody(call: BaklavaSerializableCall): Json = {
    val body = call.request.bodyString
    if (body.isEmpty) Json.Null
    else {
      val language = call.response.requestContentType.getOrElse("") match {
        case ct if ct.contains("json")       => "json"
        case ct if ct.contains("xml")        => "xml"
        case ct if ct.contains("javascript") => "javascript"
        case ct if ct.contains("html")       => "html"
        case _                               => "text"
      }
      Json.obj(
        "mode"    -> Json.fromString("raw"),
        "raw"     -> Json.fromString(body),
        "options" -> Json.obj("raw" -> Json.obj("language" -> Json.fromString(language)))
      )
    }
  }

  /** Map the first `SecurityScheme` of the operation to a Postman `auth` block using collection-level variables for credentials. Postman
    * only allows one auth per request; multiple declared schemes are represented by the first — import-time users can flip to alternates
    * in the UI.
    */
  private def requestAuth(req: BaklavaRequestContextSerializable): Json =
    req.securitySchemes.headOption match {
      case None         => Json.Null
      case Some(scheme) =>
        val s    = scheme.security
        val auth =
          if (s.httpBearer.isDefined)
            Some(
              "bearer" -> Json.arr(
                Json.obj(
                  "key"   -> Json.fromString("token"),
                  "value" -> Json.fromString(s"{{${scheme.name}Token}}"),
                  "type"  -> Json.fromString("string")
                )
              )
            )
          else if (s.httpBasic.isDefined)
            Some(
              "basic" -> Json.arr(
                Json.obj(
                  "key"   -> Json.fromString("username"),
                  "value" -> Json.fromString(s"{{${scheme.name}Username}}"),
                  "type"  -> Json.fromString("string")
                ),
                Json.obj(
                  "key"   -> Json.fromString("password"),
                  "value" -> Json.fromString(s"{{${scheme.name}Password}}"),
                  "type"  -> Json.fromString("string")
                )
              )
            )
          else if (s.apiKeyInHeader.isDefined || s.apiKeyInQuery.isDefined || s.apiKeyInCookie.isDefined) {
            val (keyName, in) =
              s.apiKeyInHeader
                .map(k => k.name -> "header")
                .orElse(s.apiKeyInQuery.map(k => k.name -> "query"))
                .orElse(s.apiKeyInCookie.map(k => k.name -> "cookie"))
                .get
            Some(
              "apikey" -> Json.arr(
                Json.obj("key" -> Json.fromString("key"), "value" -> Json.fromString(keyName), "type" -> Json.fromString("string")),
                Json.obj(
                  "key"   -> Json.fromString("value"),
                  "value" -> Json.fromString(s"{{${scheme.name}Value}}"),
                  "type"  -> Json.fromString("string")
                ),
                Json.obj("key" -> Json.fromString("in"), "value" -> Json.fromString(in), "type" -> Json.fromString("string"))
              )
            )
          } else if (
            s.oAuth2InBearer.isDefined || s.oAuth2InCookie.isDefined || s.openIdConnectInBearer.isDefined || s.openIdConnectInCookie.isDefined
          )
            Some(
              "oauth2" -> Json.arr(
                Json.obj(
                  "key"   -> Json.fromString("accessToken"),
                  "value" -> Json.fromString(s"{{${scheme.name}Token}}"),
                  "type"  -> Json.fromString("string")
                ),
                Json.obj("key" -> Json.fromString("addTokenTo"), "value" -> Json.fromString("header"), "type" -> Json.fromString("string"))
              )
            )
          else None

        auth match {
          case Some((typeName, params)) => Json.obj("type" -> Json.fromString(typeName), typeName -> params)
          case None                     => Json.Null
        }
    }

  private def responseExample(call: BaklavaSerializableCall): Json = {
    val status          = call.response.status.code
    val name            = call.request.responseDescription.getOrElse(s"$status response")
    val responseHeaders = call.response.headers.sortBy(_.name).map { h =>
      Json.obj(
        "key"   -> Json.fromString(h.name),
        "value" -> Json.fromString(h.value)
      )
    }
    Json.obj(
      "name"                     -> Json.fromString(name),
      "status"                   -> Json.fromString(statusPhrase(status)),
      "code"                     -> Json.fromInt(status),
      "header"                   -> Json.arr(responseHeaders: _*),
      "body"                     -> Json.fromString(call.response.bodyString),
      "_postman_previewlanguage" -> Json.fromString {
        call.response.responseContentType.getOrElse("") match {
          case ct if ct.contains("json") => "json"
          case ct if ct.contains("xml")  => "xml"
          case ct if ct.contains("html") => "html"
          case _                         => "text"
        }
      }
    )
  }

  private def statusPhrase(code: Int): String = code match {
    case 200 => "OK"
    case 201 => "Created"
    case 202 => "Accepted"
    case 204 => "No Content"
    case 301 => "Moved Permanently"
    case 302 => "Found"
    case 303 => "See Other"
    case 304 => "Not Modified"
    case 400 => "Bad Request"
    case 401 => "Unauthorized"
    case 403 => "Forbidden"
    case 404 => "Not Found"
    case 405 => "Method Not Allowed"
    case 409 => "Conflict"
    case 422 => "Unprocessable Entity"
    case 429 => "Too Many Requests"
    case 500 => "Internal Server Error"
    case 502 => "Bad Gateway"
    case 503 => "Service Unavailable"
    case _   => s"HTTP $code"
  }

  /** Collection-level variables: `{{baseUrl}}` plus one placeholder per declared security credential. Users fill these in once after
    * importing the collection; per-request `auth` blocks reference them.
    */
  private def collectionVariables(calls: Seq[BaklavaSerializableCall]): Seq[Json] = {
    val schemeVars = calls
      .flatMap(_.request.securitySchemes)
      .distinctBy(_.name)
      .flatMap { scheme =>
        val s = scheme.security
        if (s.httpBasic.isDefined)
          Seq(s"${scheme.name}Username", s"${scheme.name}Password")
        else if (s.apiKeyInHeader.isDefined || s.apiKeyInQuery.isDefined || s.apiKeyInCookie.isDefined)
          Seq(s"${scheme.name}Value")
        else
          Seq(s"${scheme.name}Token")
      }

    val vars = Seq("baseUrl") ++ schemeVars
    vars.map(v =>
      Json.obj(
        "key"   -> Json.fromString(v),
        "value" -> Json.fromString(""),
        "type"  -> Json.fromString("string")
      )
    )
  }
}
