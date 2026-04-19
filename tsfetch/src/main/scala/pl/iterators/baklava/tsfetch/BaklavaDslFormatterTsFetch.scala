package pl.iterators.baklava.tsfetch

import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

/** Generates a plain-TypeScript `fetch`-based client from captured calls.
  *
  * Output layout (under `target/baklava/tsfetch/`):
  *   - `package.json` / `tsconfig.json` — minimal npm package shape
  *   - `src/client.ts` — `BaklavaClient` class with `baseUrl`, credential config, and `BaklavaHttpError`
  *   - `src/types.ts` — TS interfaces derived from captured schemas
  *   - `src/{tag}.ts` — one file per `operationTag`, containing one `async function` per endpoint, plus a top-level `default.ts` for
  *     untagged operations
  *   - `src/index.ts` — re-exports everything
  *
  * Each generated function takes an instance of `BaklavaClient` plus typed path/query/body parameters and returns a `Promise<T>` where `T`
  * is the 2xx response body's TS type (or `void` when no body). Non-2xx responses throw `BaklavaHttpError`.
  */
class BaklavaDslFormatterTsFetch extends BaklavaDslFormatter {

  private val dirName        = "target/baklava/tsfetch"
  private val sourcesDirName = s"$dirName/src"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    new File(dirName).mkdirs()
    new File(sourcesDirName).mkdirs()

    BaklavaTsFetchFiles.staticFiles.foreach { case (file, content) =>
      writeFile(s"$dirName/$file", content)
    }

    config
      .get("ts-fetch-package-json")
      .foreach(pkg => writeFile(s"$dirName/package.json", pkg))

    val generator = new BaklavaTsFetchGenerator(calls)
    generator.writeClient(s"$sourcesDirName/client.ts")
    generator.writeTypes(s"$sourcesDirName/types.ts")
    val tagFiles = generator.writeTagFiles(sourcesDirName)
    generator.writeIndex(s"$sourcesDirName/index.ts", tagFiles)
  }

  private def writeFile(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
}
