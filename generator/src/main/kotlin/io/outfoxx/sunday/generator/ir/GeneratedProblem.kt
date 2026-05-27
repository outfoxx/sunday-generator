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
 * Generated RFC problem metadata.
 */
data class GeneratedProblem(
  val name: String,
  val sourceName: String? = null,
  val source: GeneratedSourceSpec? = null,
  val typeUri: String,
  val status: Int? = null,
  val title: String? = null,
  val detail: String? = null,
  val model: GeneratedTypeRef? = null,
  val statusBindings: List<GeneratedProblemStatusBinding> = listOf(),
  val payload: GeneratedProblemPayload? = null,
  val fields: List<GeneratedModelProperty> = listOf(),
  val strategy: Strategy? = null,
  val documentation: GeneratedDocumentation? = null,
) {

  /** Problem code generation strategies. */
  enum class Strategy {
    ERROR_SUBTYPE,
    PAYLOAD,
  }
}
