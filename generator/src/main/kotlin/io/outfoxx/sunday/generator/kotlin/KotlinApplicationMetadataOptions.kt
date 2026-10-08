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

import io.outfoxx.sunday.generator.utils.GeneratedProperties

/** Controls API-derived configuration resources independently of build-owned discovery artifacts. */
data class KotlinApplicationMetadataOptions(
  /** Master switch; false overrides both per-file switches. */
  val enabled: Boolean = true,
  /** Emit server configuration defaults. */
  val serverConfiguration: Boolean = true,
  /** Emit client configuration defaults. */
  val clientConfiguration: Boolean = true,
  /** Output-relative server properties resource path. */
  val serverConfigurationFileName: String = "META-INF/microprofile-config.properties",
  /** Output-relative client properties resource path. */
  val clientConfigurationFileName: String = "META-INF/microprofile-config.properties",
) {
  init {
    GeneratedProperties.validatePath(serverConfigurationFileName)
    GeneratedProperties.validatePath(clientConfigurationFileName)
  }
}
