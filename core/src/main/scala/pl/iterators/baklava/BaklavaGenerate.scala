package pl.iterators.baklava

import java.util.Base64
import scala.util.{Failure, Success}

/** Entry point the sbt plugin runs after the tests (and that a standalone script calls from `afterAll`): merges this run's captures into
  * the persistent [[BaklavaCallStore]], prunes suites whose class is gone and formats the whole store, so the documentation stays the
  * union of what each suite captured on its most recent run under sbt 2's incremental `test` and under `testOnly`.
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

  /** Context class loader first: under sbt's layered class loaders it sees the test classpath when this class's own loader does not. */
  private[baklava] def defaultLoaders: Seq[ClassLoader] =
    (Option(Thread.currentThread().getContextClassLoader).toSeq :+ getClass.getClassLoader).distinct

  private[baklava] def run(
      configMap: Map[String, String],
      store: BaklavaCallStore,
      loaders: Seq[ClassLoader],
      formatters: => Seq[BaklavaDslFormatter]
  ): Boolean = {
    val mergeResult = store.mergeRunIntoStore()
    val merged      = mergeResult match {
      case Success(suites)    => suites
      case Failure(exception) =>
        System.err.println(
          s"Baklava: failed to merge captured calls into the store: $exception. " +
            "This run's captures stay in the staging directory; `baklavaGenerate` retries the merge."
        )
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
    if (mergeResult.isSuccess) {
      store.clearStaging() match {
        case Failure(exception) => System.err.println(s"Baklava: failed to clear the staging directory: $exception")
        case Success(_)         => ()
      }
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
    * right after `baklavaClean` when sbt 2's cached `test` runs no suite (see issue #135). A view that selects zero calls is skipped the
    * same way. An invalid `views` configuration fails generation before any formatter runs.
    */
  private[baklava] def generate(
      configMap: Map[String, String],
      calls: Seq[BaklavaSerializableCall],
      formatters: => Seq[BaklavaDslFormatter]
  ): Boolean = {
    if (calls.isEmpty) {
      System.err.println(
        "Baklava: the call store is empty (0 calls) — skipping generation so existing output is not overwritten. " +
          "Nothing was captured in this run and no earlier run is stored, which is expected right after `baklavaClean` (or `clean` on sbt 1). " +
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
