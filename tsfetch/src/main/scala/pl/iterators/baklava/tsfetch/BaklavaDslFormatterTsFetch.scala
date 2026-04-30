package pl.iterators.baklava.tsfetch

import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

/** Generates a plain-TypeScript `fetch`-based client from captured calls.
  *
  * Output layout (under `target/baklava/tsfetch/`):
  *   - `package.json` / `tsconfig.json` — minimal npm package shape
  *   - `src/client.ts` — `BaklavaClient` + `BaklavaHttpError`
  *   - `src/index.ts` — re-exports
  *   - `src/common/types.ts` — types shared by two or more tags (omitted if empty)
  *   - `src/{tag}/types.ts` — types used only within one tag (omitted if empty)
  *   - `src/{tag}/endpoints.ts` — one `async function` per endpoint. Untagged operations land in `src/default/endpoints.ts`.
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
    val tagNames = generator.writeTagFolders(sourcesDirName, (relPath, content) => writeFile(s"$sourcesDirName/$relPath", content))
    generator.writeIndex(s"$sourcesDirName/index.ts", tagNames)
  }

  private def writeFile(path: String, content: String): Unit = {
    new File(path).getParentFile.mkdirs()
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
  }
}
