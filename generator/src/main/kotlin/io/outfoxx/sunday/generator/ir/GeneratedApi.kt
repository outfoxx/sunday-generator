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
 * Durable generated API intermediate representation.
 */
data class GeneratedApi(
  val irVersion: String = CURRENT_IR_VERSION,
  val name: String,
  val source: GeneratedSourceSpec,
  val services: List<GeneratedService> = listOf(),
  val models: List<GeneratedModel> = listOf(),
  val problems: List<GeneratedProblem> = listOf(),
  val auth: GeneratedAuth? = null,
  val jaxrs: GeneratedJaxrs? = null,
  val protocol: GeneratedProtocol? = null,
  val media: GeneratedMedia? = null,
  val targets: Map<String, GeneratedTarget> = mapOf(),
  val tags: List<GeneratedTag> = listOf(),
  val documentation: GeneratedDocumentation? = null,
) {

  companion object {

    /** Current durable IR schema version. */
    const val CURRENT_IR_VERSION = "1"
  }
}
