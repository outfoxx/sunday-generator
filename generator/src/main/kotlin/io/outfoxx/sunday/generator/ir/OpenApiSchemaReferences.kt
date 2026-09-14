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

/** Canonical schema names remain opaque while assertion-free wrappers are inspected without expansion. */
internal object OpenApiSchemaReferences {
  fun name(schema: Map<*, *>): String? =
    (schema["\$ref"] as? String)?.takeIf { it.startsWith("#/components/schemas/") }?.substringAfterLast('/')

  fun canonicalName(schema: Map<*, *>): String? = name(schema) ?: singleAllOfName(schema)

  fun singleAllOfName(schema: Map<*, *>): String? {
    if (schema.keys.any { it is String && it != "allOf" && OpenApiSchemaKeywords.isAssertion(it) }) return null
    val member = (schema["allOf"] as? List<*>)?.singleOrNull() as? Map<*, *> ?: return null
    return name(member) ?: singleAllOfName(member)
  }

  /** Finds a candidate only; callers must establish that the wrapper preserves its effective contract. */
  fun wrappedName(schema: Map<*, *>): String? =
    name(schema) ?: ((schema["allOf"] as? List<*>)?.singleOrNull() as? Map<*, *>)?.let(::wrappedName)
}
