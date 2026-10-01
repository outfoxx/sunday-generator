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

package io.outfoxx.sunday.generator

/** Independent environment choices used when resolving preserved source metadata. */
data class GenerationContext(
  val role: GenerationMode,
  val profile: String? = null,
  val payloadUse: PayloadUse = PayloadUse.Standalone,
  val requestTolerance: RequestTolerance = RequestTolerance.Strict,
) {
  init {
    require(profile == null || profile.isNotBlank()) { "Generation profile must not be blank" }
  }
}
