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
 * Operation-local ownership metadata for generated model declarations.
 */
data class GeneratedModelScope(
  val service: String? = null,
  val operation: String? = null,
  val securityScheme: String? = null,
  val usage: Usage,
  val name: String? = null,
  val status: Int? = null,
) {

  /** Operation-local model usage sites. */
  enum class Usage {
    PARAMETER,
    QUERY_STRING,
    REQUEST_BODY,
    RESPONSE_BODY,
    SECURITY_QUERY_STRING,
  }
}
