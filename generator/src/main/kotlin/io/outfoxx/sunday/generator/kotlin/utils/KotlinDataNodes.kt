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

package io.outfoxx.sunday.generator.kotlin.utils

import amf.core.client.platform.model.DataTypes
import amf.core.client.platform.model.domain.ArrayNode
import amf.core.client.platform.model.domain.DataNode
import amf.core.client.platform.model.domain.ObjectNode
import amf.core.client.platform.model.domain.ScalarNode
import amf.core.client.platform.model.domain.Shape
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.outfoxx.sunday.generator.utils.value
import io.outfoxx.sunday.generator.utils.values

fun DataNode.kotlinConstant(typeName: TypeName, shape: Shape?): CodeBlock {
  val builder = CodeBlock.builder()
  kotlinConstant(typeName, shape, builder)
  return builder.build()
}

fun DataNode.kotlinConstant(typeName: TypeName, shape: Shape?, builder: CodeBlock.Builder) {
  when (this) {
    is ScalarNode ->
      when (dataType().value()) {

        DataTypes.Nil() -> builder.add("null")

        DataTypes.String() ->
          if (value != null && shape?.values?.isNotEmpty() == true)
            builder.add("%T.%L", typeName, kotlinEnumName)
          else if (value != null)
            builder.add("%S", value)
          else
            builder.add("null")

        DataTypes.Date(), DataTypes.Time(), DataTypes.DateTime(), DataTypes.DateTimeOnly() ->
          if (value != null)
            builder.add("%S", value)
          else
            builder.add("null")

        DataTypes.Boolean(),
        DataTypes.Byte(), DataTypes.Integer(), DataTypes.Number(), DataTypes.Long(),
        DataTypes.Double(), DataTypes.Float(), DataTypes.Decimal() ->
          if (value != null)
            builder.add("%L", value)
          else
            builder.add("null")

        else -> error("Unsupported DataNode/DataType")
      }

    is ArrayNode -> {
      builder.add("listOf(")

      val members: List<DataNode> = members()

      members.forEachIndexed { index, member ->
        member.kotlinConstant(typeName, shape, builder)

        if (index < members.size - 1) {
          builder.add(", ")
        }
      }

      builder.add(")")
    }

    is ObjectNode -> {
      builder.add("mapOf(")

      val properties = properties().entries

      properties.forEachIndexed { index, (name, value) ->
        builder.add("%S to ", name)
        value.kotlinConstant(typeName, shape, builder)

        if (index < properties.size - 1) {
          builder.add(", ")
        }
      }

      builder.add(")")
    }

    else -> error("Unsupported DataNode")
  }
}
