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
 * Generated model declaration shared by every language emitter.
 */
data class GeneratedModel(
  val name: String,
  val kind: Kind,
  val source: GeneratedSourceSpec? = null,
  val scope: GeneratedModelScope? = null,
  val properties: List<GeneratedModelProperty> = listOf(),
  val values: List<String> = listOf(),
  val enumValueNames: List<String> = listOf(),
  val aliases: List<GeneratedTypeRef> = listOf(),
  val collection: GeneratedCollectionKind? = null,
  val closed: Boolean? = null,
  val additionalProperties: GeneratedAdditionalProperties? = null,
  val patternProperties: List<GeneratedPatternProperty> = listOf(),
  val targets: Map<String, GeneratedTarget> = mapOf(),
  val nested: GeneratedNestedType? = null,
  val patchable: Boolean = false,
  val inherits: List<GeneratedTypeRef> = listOf(),
  val discriminator: String? = null,
  val discriminatorValue: String? = null,
  val externallyDiscriminated: Boolean = false,
  val discriminatorMappings: Map<String, GeneratedTypeRef> = mapOf(),
  val validation: Map<String, String> = mapOf(),
  val serializationName: String? = null,
  val examples: List<GeneratedExample> = listOf(),
  val deprecated: Boolean = false,
  val documentation: GeneratedDocumentation? = null,
) {

  /** Shape categories represented by generated models. */
  enum class Kind {
    OBJECT,
    ENUM,
    SCALAR_ALIAS,
    UNION,
    ARRAY,
    MAP,
  }
}
