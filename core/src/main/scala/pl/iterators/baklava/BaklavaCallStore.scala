package pl.iterators.baklava

import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.{Base64, Comparator}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

/** On-disk layout of captured calls under `baseDir` (by default `target/baklava`):
  *
  *   - `calls/<suite>/<sha256>.json` — staging: what the current test run captured, written by the DSL as tests execute;
  *   - `store/<suite>/<sha256>.json` — the persistent store generation formats from.
  *
  * `<suite>` is the fully-qualified class name of the test suite that captured the call, `<sha256>` the content hash of the call JSON, so
  * identical calls within a suite collapse to one file.
  */
final class BaklavaCallStore(val baseDir: Path) {

  val stagingDir: Path = baseDir.resolve("calls")
  val storeDir: Path   = baseDir.resolve("store")

  import BaklavaSerialize.serializableCallCodec

  /** Writes `call` to staging under `suite` and returns the written file. */
  def write(suite: String, call: BaklavaSerializableCall): Try[Path] = Try {
    val jsonBytes = writeToArray(call)
    val hash      = MessageDigest.getInstance("SHA-256").digest(jsonBytes)
    val name      = Base64.getUrlEncoder.encodeToString(hash).replaceAll("=", "")
    val suiteDir  = stagingDir.resolve(suite)
    Files.createDirectories(suiteDir)
    Files.write(suiteDir.resolve(s"$name.json"), jsonBytes)
  }

  /** Moves every staging suite directory that holds at least one call into the store, replacing that suite's earlier entry wholesale.
    * Suites absent from staging are left untouched. Returns the merged suite names, sorted.
    */
  def mergeRunIntoStore(): Try[Seq[String]] = Try {
    val suites = subdirectories(stagingDir).filter(dir => jsonFiles(dir).nonEmpty)
    suites.foreach { dir =>
      val target = storeDir.resolve(dir.getFileName.toString)
      deleteRecursively(target)
      Files.createDirectories(storeDir)
      Try(Files.move(dir, target)).recover { case _ =>
        Files.createDirectories(target)
        jsonFiles(dir).foreach(file => Files.copy(file, target.resolve(file.getFileName.toString), REPLACE_EXISTING))
        deleteRecursively(dir)
        target
      }.get
    }
    suites.map(_.getFileName.toString).sorted
  }

  /** Removes store entries of suites whose class no longer exists on the classpath (deleted or renamed suites).
    *
    * A suite is kept when any of `loaders` can load its class, or fails to link it; it is removed only when every loader throws
    * `ClassNotFoundException`. Names starting with `_` (the reserved [[BaklavaCallStore.UnscopedSuite]]) are never candidates.
    *
    * Fail-safe: when the loaders evidently cannot see test classes at all, nothing is deleted and a [[BaklavaCallStore.Skipped]] names the
    * reason. That is the case when a suite merged in this run (so its class must exist) does not resolve, or when not a single stored
    * suite resolves. Keeping possibly stale calls with a warning beats wiping the store.
    */
  def pruneMissingSuites(mergedSuites: Seq[String], loaders: Seq[ClassLoader]): BaklavaCallStore.PruneOutcome = {
    val candidates = storedSuites().filterNot(_.startsWith("_"))
    val resolved   = candidates.map(suite => suite -> BaklavaCallStore.resolves(suite, loaders)).toMap
    mergedSuites.find(suite => resolved.get(suite).contains(false)) match {
      case Some(fresh) =>
        BaklavaCallStore.Skipped(
          s"suite '$fresh' was captured in this run but its class cannot be loaded; the class loader cannot see test classes"
        )
      case None if candidates.nonEmpty && !resolved.values.exists(identity) =>
        BaklavaCallStore.Skipped(s"none of the ${candidates.size} stored suites can be loaded")
      case None =>
        val gone = candidates.filterNot(resolved)
        gone.foreach(suite => deleteRecursively(storeDir.resolve(suite)))
        BaklavaCallStore.Pruned(gone)
    }
  }

  /** Deletes the staging directory, legacy flat files and empty suite directories included. */
  def clearStaging(): Try[Unit] = Try(deleteRecursively(stagingDir))

  /** Suite names present in the store, sorted. */
  def storedSuites(): Seq[String] = subdirectories(storeDir).map(_.getFileName.toString).sorted

  /** Every decodable call in the store, ordered by suite name then file name, with a call captured by several suites listed once. */
  def listStoredCalls(): Seq[BaklavaSerializableCall] = {
    val seen = mutable.HashSet.empty[String]
    for {
      suite <- subdirectories(storeDir).sortBy(_.getFileName.toString)
      file  <- jsonFiles(suite).sortBy(_.getFileName.toString)
      if seen.add(file.getFileName.toString)
      call <- readCall(file)
    } yield call
  }

  /** Every decodable call anywhere under staging, flat legacy files included, in path order. */
  def listStagedCalls(): Seq[BaklavaSerializableCall] =
    if (!Files.isDirectory(stagingDir)) Nil
    else Using.resource(Files.walk(stagingDir))(_.iterator().asScala.filter(isJsonFile).toList).sorted.flatMap(readCall)

  private def subdirectories(dir: Path): Seq[Path] =
    if (!Files.isDirectory(dir)) Nil
    else Using.resource(Files.list(dir))(_.iterator().asScala.filter(Files.isDirectory(_)).toList)

  private def jsonFiles(dir: Path): Seq[Path] =
    if (!Files.isDirectory(dir)) Nil
    else Using.resource(Files.list(dir))(_.iterator().asScala.filter(isJsonFile).toList)

  private def isJsonFile(path: Path): Boolean = Files.isRegularFile(path) && path.getFileName.toString.endsWith(".json")

  private def readCall(file: Path): Option[BaklavaSerializableCall] =
    Try(readFromArray[BaklavaSerializableCall](Files.readAllBytes(file))).toOption

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path))
      Using.resource(Files.walk(path))(_.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(Files.delete))
}

object BaklavaCallStore {

  val DefaultBaseDir: Path = Paths.get("target/baklava")

  lazy val default: BaklavaCallStore = new BaklavaCallStore(DefaultBaseDir)

  /** Suite name used for calls serialized without a suite (the deprecated two-argument `BaklavaSerialize.serializeCall`). */
  val UnscopedSuite: String = "_unscoped"

  /** The suite name for a test class: its fully-qualified name, without the `$` of an `object` spec. */
  def suiteName(clazz: Class[_]): String = clazz.getName.stripSuffix("$")

  /** Result of [[BaklavaCallStore.pruneMissingSuites]]. */
  sealed trait PruneOutcome

  /** The named suites were removed from the store (possibly none). */
  final case class Pruned(suites: Seq[String]) extends PruneOutcome

  /** Nothing was removed because the class loaders cannot be trusted to see test classes; `reason` says why. */
  final case class Skipped(reason: String) extends PruneOutcome

  /** True unless every loader throws `ClassNotFoundException` for `name`; a linkage error counts as "exists". */
  private[baklava] def resolves(name: String, loaders: Seq[ClassLoader]): Boolean =
    loaders.exists { loader =>
      try {
        Class.forName(name, false, loader)
        true
      } catch {
        case _: ClassNotFoundException => false
        case _: Throwable              => true
      }
    }
}
