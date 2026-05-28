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
 * JAX-RS target metadata carried through source IR for Quarkus generation parity.
 */
data class GeneratedJaxrs(
  val asynchronous: Boolean? = null,
  val reactive: Boolean? = null,
  val sse: GeneratedModeFlag? = null,
  val jsonBody: GeneratedModeFlag? = null,
  val context: List<String> = listOf(),
  val restClient: GeneratedJaxrsRestClient? = null,
)
