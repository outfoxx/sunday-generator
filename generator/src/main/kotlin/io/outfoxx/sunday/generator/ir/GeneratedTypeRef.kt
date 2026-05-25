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

package io.outfoxx.sunday.generator.ir

/**
 * Reference to a type used by generated IR declarations.
 */
data class GeneratedTypeRef(
  val kind: Kind,
  val name: String,
  val scope: GeneratedModelScope? = null,
  val nullable: Boolean = false,
  val arguments: List<GeneratedTypeRef> = listOf(),
  val collection: GeneratedCollectionKind? = null,
  val format: String? = null,
  val source: GeneratedSourceSpec? = null,
) {

  /** Type reference categories. */
  enum class Kind {
    SCALAR,
    NAMED,
    ARRAY,
    MAP,
    UNION,
  }

  companion object {

    /** Creates a scalar type reference. */
    fun scalar(
      name: String,
      nullable: Boolean = false,
      format: String? = null,
    ) = GeneratedTypeRef(kind = Kind.SCALAR, name = name, nullable = nullable, format = format)

    /** Creates a named type reference. */
    fun named(
      name: String,
      nullable: Boolean = false,
      arguments: List<GeneratedTypeRef> = listOf(),
      scope: GeneratedModelScope? = null,
      source: GeneratedSourceSpec? = null,
    ) = GeneratedTypeRef(
      kind = Kind.NAMED,
      name = name,
      scope = scope,
      nullable = nullable,
      arguments = arguments,
      source = source,
    )
  }
}
