package pl.iterators.baklava

import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.{Base64, Comparator}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

/** Captured calls under `baseDir` (`target/baklava` by default): `calls/<suite>/<sha256>.json` is the staging area the DSL writes while
  * tests run, `store/<suite>/<sha256>.json` the persistent store generation formats from. `<suite>` is the suite's class name, `<sha256>`
  * the content hash of the call, so identical calls within a suite collapse to one file.
  */
final class BaklavaCallStore(val baseDir: Path) {

  val stagingDir: Path = baseDir.resolve("calls")
  val storeDir: Path   = baseDir.resolve("store")

  import BaklavaSerialize.serializableCallCodec

  def write(suite: String, call: BaklavaSerializableCall): Try[Path] = Try {
    val jsonBytes = writeToArray(call)
    val hash      = MessageDigest.getInstance("SHA-256").digest(jsonBytes)
    val name      = Base64.getUrlEncoder.encodeToString(hash).replaceAll("=", "")
    val suiteDir  = stagingDir.resolve(suite)
    Files.createDirectories(suiteDir)
    Files.write(suiteDir.resolve(s"$name.json"), jsonBytes)
  }

  /** Moves each non-empty staging suite into the store, replacing that suite's earlier entry; suites absent from staging stay. */
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

  /** Removes store entries of suites whose class no longer exists. Fail-safe: when the loaders evidently cannot see test classes at all (a
    * suite merged in this run does not resolve, or no stored suite does) nothing is deleted and [[BaklavaCallStore.Skipped]] says why;
    * stale calls behind a warning beat a wiped store.
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

  def clearStaging(): Try[Unit] = Try(deleteRecursively(stagingDir))

  def storedSuites(): Seq[String] = subdirectories(storeDir).map(_.getFileName.toString).sorted

  def listStoredCalls(): Seq[BaklavaSerializableCall] = {
    val seen = mutable.HashSet.empty[String]
    for {
      suite <- subdirectories(storeDir).sortBy(_.getFileName.toString)
      file  <- jsonFiles(suite).sortBy(_.getFileName.toString)
      if seen.add(file.getFileName.toString)
      call <- readCall(file)
    } yield call
  }

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

  /** Calls serialized through the deprecated two-argument `BaklavaSerialize.serializeCall`; `_`-prefixed suites are never pruned. */
  val UnscopedSuite: String = "_unscoped"

  def suiteName(clazz: Class[_]): String = clazz.getName.stripSuffix("$")

  sealed trait PruneOutcome
  final case class Pruned(suites: Seq[String]) extends PruneOutcome
  final case class Skipped(reason: String)     extends PruneOutcome

  /** Tries `name$` too: an `object` suite nested in another type has no mirror class under the name [[suiteName]] produces. A linkage
    * error counts as "exists".
    */
  private[baklava] def resolves(name: String, loaders: Seq[ClassLoader]): Boolean =
    Seq(name, name + "$").exists(candidate =>
      loaders.exists { loader =>
        try {
          Class.forName(candidate, false, loader)
          true
        } catch {
          case _: ClassNotFoundException => false
          case _: Throwable              => true
        }
      }
    )
}
