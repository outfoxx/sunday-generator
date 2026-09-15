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

import java.util.IdentityHashMap

/** Owns completed schema analysis for one normalized document, independently of generated model allocation. */
internal class OpenApiSchemaAnalysis(
  schemas: Map<String, Map<*, *>>,
) {
  private val composition = OpenApiSchemaComposition(schemas)
  private val nullability = OpenApiSchemaNullability(composition)
  private val nullUnions = OpenApiNullUnionProjection(composition, nullability)
  private val results = IdentityHashMap<Map<*, *>, Result>()

  /** Discovery validates every normalized location even when no generated type will use it. */
  fun validateAll(schemas: Collection<Map<*, *>>) {
    composition.retainDiscriminatorTargets(schemas)
    schemas.forEach(::validate)
  }

  fun validate(schema: Map<*, *>) {
    composition.resolve(schema)
  }

  fun analyze(schema: Map<*, *>): Result =
    results[schema] ?: run {
      val effective = composition.resolve(schema)
      val projection = nullUnions.project(schema)
      Result(schema, effective, projection).also { results[schema] = it }
    }

  inner class Result(
    val source: Map<*, *>,
    val effective: Map<String, Any?>,
    val projection: OpenApiNullUnionProjection.Projection?,
  ) {
    val canonicalReference = composition.canonicalReference(source)
    val collapsedAlias = composition.collapsedAlias(source)
    val nullable: Boolean by lazy { projection?.nullable ?: nullability.isNullable(effective) }
    val metadata: Map<String, Any?> by lazy { projection?.let { composition.resolve(it.schema) } ?: effective }
    val model: OpenApiSchemaComposition.Model by lazy { composition.model(source) }
  }
}
