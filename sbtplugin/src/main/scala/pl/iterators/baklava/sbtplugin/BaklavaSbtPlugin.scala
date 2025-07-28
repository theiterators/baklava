package pl.iterators.baklava.sbtplugin

import org.typelevel.scalaccompat.annotation.unused
import sbt.*
import sbt.Keys.*
import sbt.internal.util.Attributed.data

import java.util.Base64

object BaklavaSbtPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object autoImport {
    val baklavaGenerate = taskKey[Unit]("Generate documentation using baklava")
    val baklavaClean    = taskKey[Unit]("Clean artifacts created by baklava")

    val baklavaGenerateConfigs = taskKey[Map[String, String]]("Options for baklava generate")
  }

  def settings(@unused configuration: Configuration): Seq[Setting[_]] = {
    import BaklavaSbtPlugin.autoImport.*

    val clazz = "pl.iterators.baklava.BaklavaGenerate"

    Seq[Setting[_]](
      baklavaGenerateConfigs := Map(
        "openapi-info" ->
          """
            |{
            |  "openapi" : "3.0.1",
            |  "info" : {
            |    "title" : "You can override it in your build.sbt by overriding openapi-info setting ",
            |    "version" : "1.0.7"
            |  }
            |}
            |""".stripMargin,
        "ts-rest-package-contract-json" ->
          """
            |{
            |  "name": "contracts",
            |  "version": "VERSION",
            |  "main": "index.js",
            |  "types": "index.d.ts"
            |}
            |""".stripMargin
      ),
      baklavaGenerate := {
        val configurationClassPath = (Test / fullClasspath).value: @sbtUnchecked
        val r                      = (Test / run / runner).value: @sbtUnchecked
        val s                      = streams.value: @sbtUnchecked
        val config                 = baklavaGenerateConfigs.value: @sbtUnchecked
        val serializedConfig = config.map { case (key, value) =>
          val base64EncodedValue = Base64.getEncoder.encodeToString(value.getBytes("UTF-8"))
          s"$key|$base64EncodedValue"
        }.toList

        s.log.log(Level.Info, "Running baklava generate")
        r.run(clazz, data(configurationClassPath), serializedConfig, s.log).get
      },
      baklavaClean := {
        val s = streams.value: @sbtUnchecked
        s.log.log(Level.Info, "Running baklava cleanup")
        val baklavaDir = new File("target/baklava")

        if (baklavaDir.exists()) {
          IO.delete(baklavaDir)
        }

      },
      Test / testOptions += Tests.Cleanup { () =>
        // Here we got copy paster code from baklavaGenerate task. I do not know how to use baklavaGenerate task here.
        // If I paste directly baklavaGenerate.value its invoked before the test.
        val configurationClassPath = (Test / fullClasspath).value: @sbtUnchecked
        val r                      = (Test / run / runner).value: @sbtUnchecked
        val s                      = streams.value: @sbtUnchecked
        val config                 = baklavaGenerateConfigs.value: @sbtUnchecked
        val serializedConfig = config.map { case (key, value) =>
          val base64EncodedValue = Base64.getEncoder.encodeToString(value.getBytes("UTF-8"))
          s"$key|$base64EncodedValue"
        }.toList

        s.log.log(Level.Info, "Running baklava generate")
        r.run(clazz, data(configurationClassPath), serializedConfig, s.log).get
      }
    )
  }

}
