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

package io.outfoxx.sunday.generator.ir

/** Explicit Quarkus binding, separate from the wire scheme and OAuth acquisition flow. */
data class GeneratedQuarkusSecurityBinding(
  val mode: Mode,
  val tenant: String? = null,
  /** Native suffixes relative to the selected OIDC client or tenant. */
  val properties: Map<String, String> = emptyMap(),
  /** Additional named-provider suffixes explicitly forwarded through property expressions. */
  val providerProperties: List<String> = emptyList(),
) {
  init {
    require(mode !in setOf(Mode.PROVIDER, Mode.PROPAGATE) || properties.isEmpty() && providerProperties.isEmpty()) {
      "Native OIDC properties and provider aliases are not applicable to $mode bindings"
    }
  }

  /** Merges native policy when a more-local declaration retains the same integration mode. */
  fun merge(other: GeneratedQuarkusSecurityBinding): GeneratedQuarkusSecurityBinding =
    if (mode != other.mode) {
      other
    } else {
      other.copy(
        tenant = other.tenant ?: tenant,
        properties = properties + other.properties,
        providerProperties = (providerProperties + other.providerProperties).distinct(),
      )
    }

  /** Native authentication/acquisition strategies and the optional shared server-provider SPI. */
  enum class Mode {
    PROVIDER,
    OIDC,
    WEB_APP,
    ACQUIRE,
    PROPAGATE,
    EXCHANGE,
  }
}
