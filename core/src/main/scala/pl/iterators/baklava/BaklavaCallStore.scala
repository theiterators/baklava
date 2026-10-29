package pl.iterators.baklava

import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.Base64
import scala.util.Try

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
}

object BaklavaCallStore {

  val DefaultBaseDir: Path = Paths.get("target/baklava")

  lazy val default: BaklavaCallStore = new BaklavaCallStore(DefaultBaseDir)

  /** Suite name used for calls serialized without a suite (the deprecated two-argument `BaklavaSerialize.serializeCall`). */
  val UnscopedSuite: String = "_unscoped"

  /** The suite name for a test class: its fully-qualified name, without the `$` of an `object` spec. */
  def suiteName(clazz: Class[_]): String = clazz.getName.stripSuffix("$")
}
