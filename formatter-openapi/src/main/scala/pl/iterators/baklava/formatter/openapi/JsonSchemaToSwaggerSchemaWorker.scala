package pl.iterators.baklava.formatter.openapi

import io.swagger.v3.oas.models.media._

import scala.jdk.CollectionConverters._

class JsonSchemaToSwaggerSchemaWorker {

  def convertMatch(jsonSchema: json.Schema[_]): Schema[_] =
    jsonSchema match {
      case schema: json.Schema.allof[_]            => convert(schema)
      case schema: json.Schema.array[_, _]         => convert(schema)
      case schema: json.Schema.boolean             => convert(schema)
      case schema: json.Schema.`def`[_]            => convert(schema)
      case schema: json.Schema.dictionary[_, _, _] => convert(schema)
      case schema: json.Schema.`enum`[_]           => convert(schema)
      case schema: json.Schema.integer             => convert(schema)
      case schema: json.Schema.not[_]              => convert(schema)
      case schema: json.Schema.number[_]           => convert(schema)
      case schema: json.Schema.`object`[_]         => convert(schema)
      case schema: json.Schema.oneof[_]            => convert(schema)
      case schema: json.Schema.string[_]           => convert(schema)
      case schema: json.Schema.ref[_]              => convert(schema)
      case schema: json.Schema.`value-class`[_, _] => convert(schema)
      case schema: json.Schema.const[_]            => convert(schema)
    }

  private def convert(schema: json.Schema.allof[_]): Schema[_] =
    schema.subTypes.headOption
      .map { childSchema =>
        convertMatch(childSchema)
      }
      .getOrElse(new Schema[Unit]())

  private def convert[T, C[_]](schema: json.Schema.array[T, C]): Schema[_] = {
    val output = new ArraySchema
    output.setItems(convertMatch(schema.componentType))
    output
  }

  private def convert(schema: json.Schema.boolean): Schema[_] =
    new BooleanSchema

  private def convert(schema: json.Schema.`def`[_]): Schema[_] =
    convertMatch(schema.tpe)

  private def convert[K, V, C[_, _]](schema: json.Schema.dictionary[K, V, C]): Schema[_] = {
    val output = new ObjectSchema
    output.addProperty("^.*$", convertMatch(schema.valueType))
    output
  }

  private def convert(schema: json.Schema.`enum`[_]): Schema[_] =
    convertMatch(schema.tpe) match {
      case inner: IntegerSchema =>
        val output = new IntegerSchema
        output.setEnum(schema.values.map(s => Integer.valueOf(s.toString).asInstanceOf[Number]).toList.asJava)
        output

      case inner: NumberSchema =>
        val output = new NumberSchema
        output.setEnum(schema.values.map(s => new java.math.BigDecimal(s.toString)).toList.asJava)
        output

      case _ =>
        val output = new StringSchema
        output.setEnum(schema.values.map(_.toString.stripPrefix("\"").stripSuffix("\"")).toList.asJava)
        output
    }

  private def convert(schema: json.Schema.integer): Schema[_] = new IntegerSchema

  private def convert(schema: json.Schema.not[_]): Schema[_] =
    new Schema[Unit]()

  private def convert(schema: json.Schema.number[_]): Schema[_] =
    new NumberSchema

  private def convert(schema: json.Schema.`object`[_]): Schema[_] = {
    val output = new ObjectSchema
    schema.fields.foreach { f =>
      output.addProperty(f.name, convertMatch(f.tpe))
    }
    output.setRequired(schema.fields.filter(_.required).map(_.name).toList.asJava)
    output
  }

  private def convert(schema: json.Schema.oneof[_]): Schema[_] =
    schema.subTypes.headOption
      .map { childSchema =>
        convertMatch(childSchema)
      }
      .getOrElse(new Schema[Unit]())

  private def convert(schema: json.Schema.string[_]): Schema[_] = {
    val output = new StringSchema
    schema.format.foreach { format =>
      output.setFormat(format.toString)
    }
    output
  }

  private def convert(schema: json.Schema.ref[_]): Schema[_] =
    new Schema[Unit]()

  private def convert(schema: json.Schema.`value-class`[_, _]): Schema[_] =
    convertMatch(schema.tpe)

  private def convert(schema: json.Schema.const[_]): Schema[_] =
    new StringSchema

}
