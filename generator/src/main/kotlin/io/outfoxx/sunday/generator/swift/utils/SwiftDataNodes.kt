/*
 * Copyright 2020 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.swift.utils

import amf.client.model.domain.ArrayNode
import amf.client.model.domain.DataNode
import amf.client.model.domain.ObjectNode
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.Shape
import amf.core.model.DataType
import io.outfoxx.sunday.generator.utils.value
import io.outfoxx.sunday.generator.utils.values
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.TypeName

fun DataNode.swiftConstant(typeName: TypeName, shape: Shape?): CodeBlock {
  val builder = CodeBlock.builder()
  swiftConstant(typeName, shape, builder)
  return builder.build()
}

fun DataNode.swiftConstant(typeName: TypeName, shape: Shape?, builder: CodeBlock.Builder) {
  when (this) {
    is ScalarNode ->
      when (dataType().value()) {

        DataType.Nil() -> builder.add("null")

        DataType.String() ->
          if (value != null && shape?.values?.isNotEmpty() == true)
            builder.add("%T.%L", typeName, swiftIdentifierName)
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
        member.swiftConstant(typeName, shape, builder)

        if (index < members.size - 1) {
          builder.add(", ")
        }
      }

      builder.add("]")
    }

    is ObjectNode -> {
      builder.add("[")

      val properties = properties().entries

      properties.forEachIndexed { index, (name, value) ->
        builder.add("%S: ", name)
        value.swiftConstant(typeName, shape, builder)

        if (index < properties.size - 1) {
          builder.add(", ")
        }
      }

      builder.add("]")
    }

    else -> error("Unsupported DataNode")
  }
}
