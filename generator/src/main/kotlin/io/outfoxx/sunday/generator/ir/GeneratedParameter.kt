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
 * Operation parameter carried by generated IR.
 */
data class GeneratedParameter(
  val name: String,
  val location: Location,
  val type: GeneratedTypeRef,
  val required: Boolean = false,
  val serializationName: String? = null,
  val defaultValue: Any? = null,
  val constantValue: Any? = null,
  val encoding: GeneratedParameterEncoding? = null,
  val validation: Map<String, String> = mapOf(),
  val examples: List<GeneratedExample> = listOf(),
  val deprecated: Boolean = false,
  val documentation: GeneratedDocumentation? = null,
) {

  /** Wire locations supported by operation parameters. */
  enum class Location {
    PATH,
    QUERY,
    HEADER,
    COOKIE,
    BODY,
    MESSAGE,
  }
}
