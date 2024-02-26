package pl.iterators.baklava.sbtplugin

import sbt.Keys._
import sbt._
import sbt.internal.util.Attributed.data

import java.nio.file.Paths

object BaklavaSbtPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object model {
    object Fetchers extends Enumeration {
      type Fetcher = Value

      val Specs2Fetcher, ScalatestFetcher = Value
    }

    object Formatters extends Enumeration {
      type Formatter = Value

      val SimpleDocsFormatter, OpenApiFormatter, TsFormatter, TsStrictFormatter = Value
    }
  }
  import model._

  object autoImport {
    // settings
    val baklavaTestClassPackage = settingKey[String]("Test class package")
    val baklavaOutputDir        = settingKey[String]("Output directory")
    val baklavaFetcher          = settingKey[Fetchers.Fetcher]("Selected fetcher")
    val baklavaFormatters       = settingKey[Seq[Formatters.Formatter]]("Selected formatters")

    // tasks
    val baklavaGenerate = taskKey[Unit]("Generate documentation using baklava")
    val baklavaClean    = taskKey[Unit]("Clean baklava resources")
  }

  def settings(configuration: Configuration): Seq[Setting[_]] = {
    import BaklavaSbtPlugin.autoImport._

    Seq[Setting[_]](
      baklavaTestClassPackage := "",
      baklavaOutputDir := (target.value / "baklava").toString,
      baklavaFetcher := Fetchers.Specs2Fetcher,
      baklavaFormatters := Seq(Formatters.SimpleDocsFormatter),
      fork in baklavaGenerate := true,
      baklavaGenerate := {
        val rootPackage = baklavaTestClassPackage.value
        val output      = baklavaOutputDir.value
        val fetcher     = baklavaFetcher.value
        val formatters  = baklavaFormatters.value
        if (rootPackage.isEmpty)
          throw new IllegalStateException("baklavaTestClassPackage is not defined")

        val arguments = Seq(rootPackage, output, fetcher.toString) ++ formatters.map(_.toString)

        val clazz = "pl.iterators.baklava.generator.Main"

        val configurationClassPath = (fullClasspath in configuration).value
        val r                      = (runner in (configuration, run)).value
        val s                      = streams.value
        r.run(clazz, data(configurationClassPath), arguments, s.log).get
      },
      baklavaClean := {
        def deleteDirectory(path: File): Unit = {
          if (path.exists) {
            path.listFiles.foreach { file =>
              if (file.isDirectory) deleteDirectory(file)
              else file.delete
            }
          }
          path.delete
        }

        deleteDirectory(Paths.get(baklavaOutputDir.value).toFile)
      }
    )
  }

}
