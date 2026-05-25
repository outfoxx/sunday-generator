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
 * Generated operation declaration.
 */
data class GeneratedOperation(
  val id: String,
  val method: String,
  val path: String,
  val parameters: List<GeneratedParameter> = listOf(),
  val queryString: GeneratedTypeRef? = null,
  val requestBody: GeneratedPayload? = null,
  val responses: List<GeneratedResponse> = listOf(),
  val exchange: GeneratedExchange? = null,
  val problems: List<GeneratedTypeRef> = listOf(),
  val nullify: GeneratedNullify? = null,
  val auth: GeneratedAuth? = null,
  val protocol: GeneratedProtocol? = null,
  val media: GeneratedMedia? = null,
  val policy: GeneratedPolicy? = null,
  val streaming: GeneratedStreaming? = null,
  val jaxrs: GeneratedJaxrs? = null,
  val deprecated: Boolean = false,
  val tags: List<String> = listOf(),
  val documentation: GeneratedDocumentation? = null,
)
