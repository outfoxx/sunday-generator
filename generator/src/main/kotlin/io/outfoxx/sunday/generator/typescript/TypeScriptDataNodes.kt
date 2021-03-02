package io.outfoxx.sunday.generator.typescript

import amf.client.model.domain.ArrayNode
import amf.client.model.domain.DataNode
import amf.client.model.domain.ObjectNode
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.Shape
import amf.core.model.DataType
import io.outfoxx.sunday.generator.value
import io.outfoxx.sunday.generator.values
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.TypeName

fun DataNode.typeScriptConstant(typeName: TypeName, shape: Shape?): CodeBlock {
  val builder = CodeBlock.builder()
  typeScriptConstant(typeName, shape, builder)
  return builder.build()
}

fun DataNode.typeScriptConstant(typeName: TypeName, shape: Shape?, builder: CodeBlock.Builder) {
  when (this) {
    is ScalarNode ->
      when (dataType().value()) {

        DataType.Nil() -> builder.add("null")

        DataType.String() ->
          if (value != null && shape?.values?.isNotEmpty() == true)
            builder.add("%T.%L", typeName, typeScriptEnumName)
          else if (value != null)
            builder.add("%S", value)
          else
            builder.add("null")

        DataType.Date(), DataType.Time(), DataType.DateTime(), DataType.DateTimeOnly() ->
          if (value != null)
            builder.add("%S", value)
          else
            builder.add("null")

        DataType.Boolean(),
        DataType.Byte(), DataType.Integer(), DataType.Number(), DataType.Long(),
        DataType.Double(), DataType.Float(), DataType.Decimal() ->
          if (value != null)
            builder.add("%L", value)
          else
            builder.add("null")

        else -> error("Unsupported DataNode/DataType")
      }

    is ArrayNode -> {
      builder.add("[")

      val members: List<DataNode> = members()

      members.forEachIndexed { index, member ->
        member.typeScriptConstant(typeName, shape, builder)

        if (index < members.size - 1) {
          builder.add(", ")
        }
      }

      builder.add("]")
    }

    is ObjectNode -> {
      builder.add("{")

      val properties = properties().entries

      properties.forEachIndexed { index, (name, value) ->
        builder.add("%S: ", name)
        value.typeScriptConstant(typeName, shape, builder)

        if (index < properties.size - 1) {
          builder.add(", ")
        }
      }

      builder.add("}")
    }

    else -> error("Unsupported DataNode")
  }
}
