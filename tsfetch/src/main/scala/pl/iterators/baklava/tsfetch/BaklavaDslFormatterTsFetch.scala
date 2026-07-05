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
  *   - `src/common/types.ts` — types shared by two or more route-area modules (omitted if empty)
  *   - `src/{area}/types.ts` — types used only within one module (omitted if empty)
  *   - `src/{area}/endpoints.ts` — one `async function` per endpoint. Modules follow the shared path-derived boundaries (`/users/...` →
  *     `users/`, with a `v<N>` prefix treated as organizational: `/v1/auctions/...` → `v1/auctions/`).
  *
  * Each generated function takes an instance of `BaklavaClient` plus typed path/query/body parameters and returns a `Promise<T>` where `T`
  * is the 2xx response body's TS type (or `void` when no body). Non-2xx responses throw `BaklavaHttpError`.
  */
class BaklavaDslFormatterTsFetch extends BaklavaDslFormatter {

  private val dirName        = "target/baklava/tsfetch"
  private val sourcesDirName = s"$dirName/src"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    // Module folders are named after the current route set; without a wipe, folders from a
    // previous run (renamed or removed routes) would linger and ship to consumers syncing the
    // directory.
    deleteRecursively(new File(sourcesDirName))
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
    generator.writeModuleFolders((relPath, content) => writeFile(s"$sourcesDirName/$relPath", content))
    generator.writeIndex(s"$sourcesDirName/index.ts")
  }

  private def writeFile(path: String, content: String): Unit = {
    new File(path).getParentFile.mkdirs()
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
  }

  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) Option(file.listFiles()).toSeq.flatten.foreach(deleteRecursively)
    val _ = file.delete()
  }
}
