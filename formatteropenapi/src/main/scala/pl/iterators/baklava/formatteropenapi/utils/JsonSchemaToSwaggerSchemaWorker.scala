package pl.iterators.baklava.formatteropenapi.utils

import io.swagger.v3.oas.models.media._

import scala.collection.JavaConverters._

class JsonSchemaToSwaggerSchemaWorker {

  def convert(jsonSchema: json.Schema[_]): Schema[_] = {
    jsonSchema match {
      case schema: json.Schema.allof[Any]                => convert(schema)
      case schema: json.Schema.array[Any, Any]           => convert(schema)
      case schema: json.Schema.boolean                   => convert(schema)
      case schema: json.Schema.`def`[Any]                => convert(schema)
      case schema: json.Schema.dictionary[Any, Any, Any] => convert(schema)
      case schema: json.Schema.`enum`[Any]               => convert(schema)
      case schema: json.Schema.integer                   => convert(schema)
      case schema: json.Schema.not[Any]                  => convert(schema)
      case schema: json.Schema.number[Any]               => convert(schema)
      case schema: json.Schema.`object`[Any]             => convert(schema)
      case schema: json.Schema.oneof[Any]                => convert(schema)
      case schema: json.Schema.string[Any]               => convert(schema)
      case schema: json.Schema.ref[Any]                  => convert(schema)
      case schema: json.Schema.`value-class`[Any, Any]   => convert(schema)
    }
  }

  private def convert(schema: json.Schema.allof[Any]): Schema[_] = {
    schema.subTypes.headOption
      .map { childSchema =>
        convert(childSchema)
      }
      .getOrElse(new Schema[Unit]())
  }

  private def convert(schema: json.Schema.array[Any, Any]): Schema[_] = {
    val output = new ArraySchema
    output.setItems(convert(schema.componentType))
    output
  }

  private def convert(schema: json.Schema.boolean): Schema[_] =
    new BooleanSchema

  private def convert(schema: json.Schema.`def`[Any]): Schema[_] =
    convert(schema.tpe)

  private def convert(schema: json.Schema.dictionary[Any, Any, Any]): Schema[_] = {
    val output = new ObjectSchema
    output.addProperties("^.*$", convert(schema.valueType))
    output
  }

  private def convert(schema: json.Schema.`enum`[Any]): Schema[_] = {
    convert(schema.tpe) match {
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

  }

  private def convert(schema: json.Schema.integer): Schema[_] = new IntegerSchema

  private def convert(schema: json.Schema.not[Any]): Schema[_] =
    new Schema[Unit]()

  private def convert(schema: json.Schema.number[Any]): Schema[_] =
    new NumberSchema

  private def convert(schema: json.Schema.`object`[Any]): Schema[_] = {
    val output = new ObjectSchema
    schema.fields.foreach { f =>
      output.addProperties(f.name, convert(f.tpe))
    }
    output.setRequired(schema.fields.filter(_.required).map(_.name).toList.asJava)
    output
  }

  private def convert(schema: json.Schema.oneof[Any]): Schema[_] = {
    schema.subTypes.headOption
      .map { childSchema =>
        convert(childSchema)
      }
      .getOrElse(new Schema[Unit]())
  }

  private def convert(schema: json.Schema.string[Any]): Schema[_] = {
    val output = new StringSchema
    schema.format.foreach { format =>
      output.setFormat(format.toString)
    }
    output
  }

  private def convert(schema: json.Schema.ref[Any]): Schema[_] =
    new Schema[Unit]()

  private def convert(schema: json.Schema.`value-class`[Any, Any]): Schema[_] =
    convert(schema.tpe)

}
