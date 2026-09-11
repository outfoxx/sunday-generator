/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.genError
import java.util.IdentityHashMap

/** Reduces a bounded null-bearing union to its non-null payload and independent IR nullability. */
internal class OpenApiNullUnionProjection(
  private val composition: OpenApiSchemaComposition,
  private val nullability: OpenApiSchemaNullability,
) {
  private val projections = IdentityHashMap<Map<*, *>, Projection?>()

  class Projection(
    val schema: Map<*, *>,
    val nullable: Boolean,
  )

  fun project(schema: Map<*, *>): Projection? {
    val effective = composition.resolve(schema)
    if (projections.containsKey(effective)) return projections[effective]
    return reduce(effective, schema).also { projections[effective] = it }
  }

  private fun reduce(
    effective: Map<String, Any?>,
    origin: Map<*, *>,
  ): Projection? {
    val keywords = listOf("oneOf", "anyOf").filter { effective[it] is List<*> }
    val branches = keywords.flatMap { effective[it] as List<*> }
    val nullBranches = branches.filterIsInstance<Map<*, *>>().filter { isNullOnly(composition.resolve(it)) }
    if (nullBranches.isEmpty()) return null
    if (keywords.size != 1 || branches.size != 2 || nullBranches.size != 1) {
      fail(origin, "expected one payload branch and one null-only branch")
    }
    val payload =
      branches.single { it !== nullBranches.single() } as? Map<*, *>
        ?: fail(origin, "the payload is not a representable schema")
    val nullable =
      nullability.acceptsNull(effective)
        ?: fail(origin, "null acceptance cannot be determined from schema metadata")
    if (nullability.acceptsNull(nullBranches.single()) == null) {
      fail(origin, "null-only branch assertions cannot be evaluated")
    }
    val payloadSchema = composition.resolve(payload)
    val nonNullPayload = withoutNull(payloadSchema, origin)
    val wrapper = withoutNull(effective.filterKeys { it !in keywords }, origin)
    val constrained = composition.constrain(nonNullPayload, wrapper, origin)
    if (nullability.acceptsNull(constrained) != false) {
      fail(origin, "the non-null payload cannot be represented without expanding its schema")
    }

    // A nullable named alias carries null in its declaration as well as at its uses. Narrowing
    // such an alias needs a separate wrapper declaration, never a mutation of the target.
    val unchanged = assertions(constrained) == assertions(nonNullPayload)
    val reference =
      OpenApiSchemaComposition.referenceName(payload) != null || OpenApiSchemaComposition.isSingleReference(payload)
    val keepReference = reference && unchanged && (nullable || nullability.acceptsNull(payloadSchema) == false)
    val fields =
      if (keepReference) {
        payload.entries.associate { it.key.toString() to it.value } +
          wrapper.filterKeys { !OpenApiSchemaComposition.isAssertion(it) }
      } else {
        constrained
      }
    return Projection((origin as? OpenApiSchema)?.withFields(fields, false) ?: fields, nullable)
  }

  private fun withoutNull(
    schema: Map<String, Any?>,
    origin: Map<*, *>,
  ): Map<String, Any?> {
    if (schema.keys.any { it in setOf("oneOf", "anyOf", "not", "if", "then", "else") }) {
      fail(origin, "nested unions, conditional assertions, or recursive union expansion are unsupported")
    }
    val result = schema.toMutableMap()
    result.remove("nullable")
    when (val type = result["type"]) {
      "null" -> fail(origin, "the intersection has no non-null payload")
      is List<*> -> {
        val types = type.filterNot { it == "null" }
        if (types.size != 1) fail(origin, "the payload must have one non-null type")
        result["type"] = types.single()
      }
    }
    (result["enum"] as? List<*>)?.let { values ->
      result["enum"] =
        values.filterNotNull().also {
          if (it.isEmpty()) fail(origin, "the intersection has no non-null enum value")
        }
    }
    if (result.containsKey("const") && result["const"] == null) {
      fail(origin, "the intersection has no non-null constant")
    }
    return result
  }

  private fun isNullOnly(schema: Map<String, Any?>): Boolean =
    schema["type"] == "null" ||
      schema["type"] == listOf("null") ||
      (schema.containsKey("const") && schema["const"] == null) ||
      schema["enum"] == listOf(null)

  private fun assertions(schema: Map<String, Any?>): Map<String, Any?> =
    schema.filterKeys(OpenApiSchemaComposition::isAssertion)

  private fun fail(
    origin: Map<*, *>,
    detail: String,
  ): Nothing {
    val message = "Unsupported OpenAPI null-union projection: $detail"
    (origin as? OpenApiSchema)?.error(message) ?: genError(message)
  }
}
