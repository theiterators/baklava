package pl.iterators.baklava

import io.circe.Json
import io.circe.parser.parse

import scala.util.Try
import scala.util.matching.Regex

/** A named subset of the captured calls that every formatter renders once more, under `target/baklava/views/<name>/`, next to the full
  * output. Configured through the `views` key of `baklavaGenerateConfigs` as a JSON array of `{ "name": ..., "filter": ... }` objects,
  * where `filter` is one [[BaklavaViewFilter]] expression or an array of them that must all hold. A view with no `filter` selects
  * everything.
  */
final case class BaklavaView(name: String, filters: Seq[BaklavaViewFilter]) {
  def outputRoot(base: String): String = s"$base/views/$name"

  def select(calls: Seq[BaklavaSerializableCall]): Seq[BaklavaSerializableCall] =
    calls.filter(call => filters.forall(_.matches(call)))
}

object BaklavaView {
  val ConfigKey: String = "views"

  private val NamePattern: Regex = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def fromConfig(config: Map[String, String]): Either[String, Seq[BaklavaView]] =
    config.get(ConfigKey).map(_.trim).filter(_.nonEmpty) match {
      case None      => Right(Seq.empty)
      case Some(raw) => parseViews(raw)
    }

  def parseViews(raw: String): Either[String, Seq[BaklavaView]] =
    for {
      json  <- parse(raw).left.map(failure => s"not valid JSON: ${failure.getMessage}")
      items <- json.asArray.toRight("expected a JSON array of { \"name\": ..., \"filter\": ... } objects")
      views <- traverse(items)(parseView)
      _     <- duplicates(views.map(_.name)) match {
        case Nil  => Right(())
        case dups => Left(s"view names must be unique, duplicated: ${dups.mkString(", ")}")
      }
    } yield views

  private def parseView(json: Json): Either[String, BaklavaView] = {
    val cursor = json.hcursor
    for {
      name    <- cursor.get[String]("name").left.map(_ => s"every view needs a string \"name\", got: ${json.noSpaces}")
      _       <- Either.cond(NamePattern.matches(name), (), s"view name '$name' must match ${NamePattern.regex}")
      filters <- cursor.downField("filter").focus match {
        case None                            => Right(Seq(BaklavaViewFilter.All))
        case Some(single) if single.isString => single.asString.toRight("").flatMap(BaklavaViewFilter.parse).map(Seq(_))
        case Some(many) if many.isArray      =>
          traverse(many.asArray.getOrElse(Vector.empty)) { item =>
            item.asString.toRight(s"view '$name': filter entries must be strings, got: ${item.noSpaces}").flatMap(BaklavaViewFilter.parse)
          }
        case Some(other) => Left(s"view '$name': \"filter\" must be a string or an array of strings, got: ${other.noSpaces}")
      }
    } yield BaklavaView(name, filters)
  }

  private def traverse[A, B](items: Seq[A])(f: A => Either[String, B]): Either[String, Seq[B]] =
    items.foldLeft[Either[String, Vector[B]]](Right(Vector.empty)) { (acc, item) =>
      for {
        done <- acc
        next <- f(item)
      } yield done :+ next
    }

  private def duplicates(names: Seq[String]): List[String] =
    names.groupBy(identity).collect { case (name, occurrences) if occurrences.sizeIs > 1 => name }.toList.sorted
}

/** One predicate of a view filter. Paths are matched against the symbolic path (`/users/{id}`), as a regex search, so `^/admin/` means
  * "under /admin".
  */
sealed trait BaklavaViewFilter {
  def matches(call: BaklavaSerializableCall): Boolean
}

object BaklavaViewFilter {
  case object All extends BaklavaViewFilter {
    override def matches(call: BaklavaSerializableCall): Boolean = true
  }

  final case class TagIncludes(tag: String) extends BaklavaViewFilter {
    override def matches(call: BaklavaSerializableCall): Boolean = call.request.operationTags.contains(tag)
  }

  final case class TagExcludes(tag: String) extends BaklavaViewFilter {
    override def matches(call: BaklavaSerializableCall): Boolean = !call.request.operationTags.contains(tag)
  }

  final case class PathIncludes(pattern: String) extends BaklavaViewFilter {
    private val regex                                            = pattern.r
    override def matches(call: BaklavaSerializableCall): Boolean = regex.findFirstIn(call.request.symbolicPath).isDefined
  }

  final case class PathExcludes(pattern: String) extends BaklavaViewFilter {
    private val regex                                            = pattern.r
    override def matches(call: BaklavaSerializableCall): Boolean = regex.findFirstIn(call.request.symbolicPath).isEmpty
  }

  private val Usage = "all, tag-includes:<tag>, tag-excludes:<tag>, path-includes:<regex> or path-excludes:<regex>"

  def parse(raw: String): Either[String, BaklavaViewFilter] = {
    val trimmed          = raw.trim
    val (kind, argument) = trimmed.indexOf(':') match {
      case -1    => (trimmed, "")
      case index => (trimmed.substring(0, index).trim, trimmed.substring(index + 1).trim)
    }
    (kind, argument) match {
      case ("all", "")                                    => Right(All)
      case ("tag-includes", tag) if tag.nonEmpty          => Right(TagIncludes(tag))
      case ("tag-excludes", tag) if tag.nonEmpty          => Right(TagExcludes(tag))
      case ("path-includes", pattern) if pattern.nonEmpty => validRegex(pattern).map(PathIncludes.apply)
      case ("path-excludes", pattern) if pattern.nonEmpty => validRegex(pattern).map(PathExcludes.apply)
      case _                                              => Left(s"unknown view filter '$trimmed'; expected $Usage")
    }
  }

  private def validRegex(pattern: String): Either[String, String] =
    Try(pattern.r).toEither.left.map(failure => s"invalid regex '$pattern': ${failure.getMessage}").map(_ => pattern)
}
