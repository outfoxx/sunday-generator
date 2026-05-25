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
 * Security scheme metadata needed by generated clients and servers.
 */
data class GeneratedSecurityScheme(
  val name: String,
  val type: String? = null,
  val scheme: String? = null,
  val bearerFormat: String? = null,
  val headers: List<GeneratedParameter> = listOf(),
  val queryParameters: List<GeneratedParameter> = listOf(),
  val cookieParameters: List<GeneratedParameter> = listOf(),
  val queryString: GeneratedTypeRef? = null,
  val documentation: GeneratedDocumentation? = null,
)
