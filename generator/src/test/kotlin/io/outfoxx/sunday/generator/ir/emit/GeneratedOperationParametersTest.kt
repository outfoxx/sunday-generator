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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedOperationParametersTest {

  @Test
  fun `builds operation parameter views with generated names and wire names`() {
    val first = parameter("project-id", GeneratedParameter.Location.PATH, serializationName = "project-id")
    val second = parameter("project-id", GeneratedParameter.Location.QUERY, serializationName = "project-id")
    val operation =
      operation(
        first,
        second,
      )
    var count = 0

    val parameters =
      operation.operationParameterViews(
        identifierName = { parameter -> parameter.name.replace("-", "") },
        allocateName = { _, proposedName ->
          count += 1
          "$proposedName$count"
        },
      )

    assertEquals(listOf("projectid1", "projectid2"), parameters.map { parameter -> parameter.name })
    assertEquals(listOf("project-id", "project-id"), parameters.map { parameter -> parameter.wireName })
    assertSame(first, parameters[0].source)
    assertSame(second, parameters[1].source)
  }

  @Test
  fun `exposes default constant and null filtering metadata`() {
    val optional = parameter("filter", GeneratedParameter.Location.QUERY)
    val defaulted = parameter("limit", GeneratedParameter.Location.QUERY, required = true, defaultValue = 25)
    val constant = parameter("version", GeneratedParameter.Location.HEADER, required = true, constantValue = "v1")

    val parameters = operation(optional, defaulted, constant).operationParameterViews()

    assertTrue(parameters[0].isNullable)
    assertTrue(parameters[0].shouldFilterNullValue)
    assertFalse(parameters[1].isNullable)
    assertEquals(25, parameters[1].defaultValue)
    assertTrue(parameters[2].isConstant)
    assertFalse(parameters[2].shouldFilterNullValue)
    assertEquals("v1", parameters[2].constantValue)
  }

  @Test
  fun `filters parameter views by location`() {
    val parameters =
      operation(
        parameter("projectId", GeneratedParameter.Location.PATH, required = true),
        parameter("filter", GeneratedParameter.Location.QUERY),
        parameter("traceId", GeneratedParameter.Location.HEADER),
      ).operationParameterViews()

    assertEquals(
      listOf("projectId"),
      parameters
        .withLocation(GeneratedParameter.Location.PATH)
        .map { parameter -> parameter.name },
    )
    assertEquals(
      listOf("filter"),
      parameters
        .withLocation(GeneratedParameter.Location.QUERY)
        .map { parameter -> parameter.name },
    )
    assertEquals(
      listOf("traceId"),
      parameters
        .withLocation(GeneratedParameter.Location.HEADER)
        .map { parameter -> parameter.name },
    )
  }

  private fun operation(vararg parameters: GeneratedParameter) =
    GeneratedOperation(
      id = "fetch",
      method = "GET",
      path = "/projects/{projectId}",
      parameters = parameters.toList(),
    )

  private fun parameter(
    name: String,
    location: GeneratedParameter.Location,
    required: Boolean = false,
    serializationName: String? = null,
    defaultValue: Any? = null,
    constantValue: Any? = null,
  ) = GeneratedParameter(
    name = name,
    location = location,
    type = GeneratedTypeRef.scalar("string"),
    required = required,
    serializationName = serializationName,
    defaultValue = defaultValue,
    constantValue = constantValue,
  )
}
