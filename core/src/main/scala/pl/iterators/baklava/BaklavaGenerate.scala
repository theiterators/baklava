package pl.iterators.baklava

import java.util.Base64
import scala.util.{Failure, Success}

/** Entry point the sbt plugin runs after the tests (and that a standalone script calls from `afterAll`).
  *
  * Captured calls flow staging → store → formatters, see [[BaklavaCallStore]]: the calls this run captured are merged into the persistent
  * store, suite by suite, replacing each re-run suite's previous calls; store entries of suites whose class no longer exists are removed;
  * and every formatter runs over the whole store. Documentation is therefore the union of what each suite captured on its most recent run,
  * which keeps it complete under sbt 2's incremental `test` and under `testOnly`.
  */
object BaklavaGenerate {
  def main(args: Array[String]): Unit = {
    val configMap = args.map { entry =>
      val splitIndex = entry.indexOf('|')
      if (splitIndex >= 0) {
        val key          = entry.substring(0, splitIndex)
        val encodedValue = entry.substring(splitIndex + 1)
        val value        = new String(Base64.getDecoder.decode(encodedValue), "UTF-8")
        key -> value
      } else {
        entry -> ""
      }
    }.toMap

    run(configMap, BaklavaCallStore.default, defaultLoaders, BaklavaDslFormatter.formatters)
    ()
  }

  /** The thread's context class loader first (sbt's in-process runner points it at the full test classpath, including under class-loader
    * layering strategies that hide project classes from this class's own loader), then this class's loader.
    */
  private[baklava] def defaultLoaders: Seq[ClassLoader] =
    (Option(Thread.currentThread().getContextClassLoader).toSeq :+ getClass.getClassLoader).distinct

  /** Merges this run's captures into `store`, prunes suites whose class `loaders` cannot find, clears staging, and runs the formatters
    * over the whole store. The store is never cleared: it is what the next run builds on. Returns whether the formatters ran.
    */
  private[baklava] def run(
      configMap: Map[String, String],
      store: BaklavaCallStore,
      loaders: Seq[ClassLoader],
      formatters: => Seq[BaklavaDslFormatter]
  ): Boolean = {
    val merged = store.mergeRunIntoStore() match {
      case Success(suites)    => suites
      case Failure(exception) =>
        System.err.println(s"Baklava: failed to merge captured calls into the store: $exception")
        Nil
    }
    store.pruneMissingSuites(merged, loaders) match {
      case BaklavaCallStore.Pruned(suites) =>
        suites.foreach(suite => System.err.println(s"Baklava: removed stored calls of '$suite' — its class no longer exists."))
      case BaklavaCallStore.Skipped(reason) =>
        System.err.println(
          s"Baklava: skipped pruning stored suites — $reason. Stale suites may remain in the output; " +
            "run `baklavaClean` and a full test run to reset the store."
        )
    }
    store.clearStaging() match {
      case Failure(exception) => System.err.println(s"Baklava: failed to clear the staging directory: $exception")
      case Success(_)         => ()
    }
    val calls = store.listStoredCalls()
    val kept  = store.storedSuites().size - merged.size
    System.err.println(
      s"Baklava: merged ${merged.size} suite(s) from this run, kept $kept from earlier runs, formatting ${calls.size} call(s)."
    )
    generate(configMap, calls, formatters)
  }

  /** Runs the formatters over the stored calls, then once more per configured view (see [[BaklavaView]]) with that view's subset of the
    * calls under `target/baklava/views/<name>`. With zero calls generation is skipped (returning false), so a previously generated spec is
    * never overwritten by an empty one — an empty store means nothing was captured in this run and nothing is left from earlier runs, e.g.
    * right after `clean` when sbt 2's cached `test` runs no suite (see issue #135). A view that selects zero calls is skipped the same
    * way. An invalid `views` configuration fails generation before any formatter runs.
    */
  private[baklava] def generate(
      configMap: Map[String, String],
      calls: Seq[BaklavaSerializableCall],
      formatters: => Seq[BaklavaDslFormatter]
  ): Boolean = {
    if (calls.isEmpty) {
      System.err.println(
        "Baklava: the call store is empty (0 calls) — skipping generation so existing output is not overwritten. " +
          "Nothing was captured in this run and no earlier run is stored, which is expected right after `clean`. " +
          "If your tests did run, make sure they use the Baklava DSL. " +
          "On sbt 2 a warm cache may have skipped every suite: run `testFull` once to rebuild the store; " +
          "afterwards plain `test` keeps it current."
      )
      false
    } else {
      val views = BaklavaView.fromConfig(configMap) match {
        case Right(parsed) => parsed
        case Left(error)   => sys.error(s"Baklava: invalid '${BaklavaView.ConfigKey}' configuration: $error")
      }
      formatters.foreach(_.create(configMap, calls))
      views.foreach { view =>
        val selected = view.select(calls)
        if (selected.isEmpty) {
          System.err.println(
            s"Baklava: view '${view.name}' selected 0 of ${calls.size} captured calls — skipping its output so nothing empty is written."
          )
        } else {
          formatters.foreach(_.create(configMap, selected, view.outputRoot(BaklavaDslFormatter.DefaultOutputRoot)))
        }
      }
      true
    }
  }
}
