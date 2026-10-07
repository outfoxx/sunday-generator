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

package io.outfoxx.sunday.generator.kotlin

/** Controls API-derived Quarkus configuration sources, independently of build-owned discovery artifacts. */
data class KotlinApplicationMetadataOptions(
  /** Master switch; false overrides both per-file switches. */
  val enabled: Boolean = true,
  /** Emit server OIDC defaults and their discovery registration. */
  val serverConfiguration: Boolean = true,
  /** Emit client OIDC defaults and their discovery registration. */
  val clientConfiguration: Boolean = true,
  /** Kotlin basename in the generated service package; also determines the configuration class name. */
  val serverConfigurationFileName: String = "OpenAPIServerOidcConfiguration.kt",
  /** Kotlin basename in the generated service package; also determines the configuration class name. */
  val clientConfigurationFileName: String = "OpenAPIOidcConfiguration.kt",
) {
  init {
    listOf(serverConfigurationFileName, clientConfigurationFileName).forEach { name ->
      require(Regex("[A-Z][A-Za-z0-9_]*\\.kt").matches(name)) {
        "Application metadata filename must be an uppercase Kotlin class basename ending in .kt: $name"
      }
    }
  }
}
