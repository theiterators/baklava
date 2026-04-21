package pl.iterators.baklava.postman

import io.circe.Printer
import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

/** Generates a Postman Collection v2.1.0 JSON file from captured calls. The file imports cleanly into Postman and Insomnia (via its
  * Postman v2 import path).
  *
  * Collection-level variables are emitted for `{{baseUrl}}` and each declared security credential placeholder; users fill them in once
  * after import instead of per-request.
  */
class BaklavaDslFormatterPostman extends BaklavaDslFormatter {

  private val dirName  = "target/baklava/postman"
  private val fileName = "collection.json"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    val dir = new File(dirName)
    dir.mkdirs()

    val collectionName = config.getOrElse("postman.collectionName", "Baklava-generated API")

    val json = BaklavaPostmanCollection.build(collectionName, calls)

    Using.resource(new PrintWriter(new FileWriter(s"$dirName/$fileName"))) { pw =>
      pw.print(json.printWith(Printer.spaces2.copy(dropNullValues = true)))
    }
  }
}
