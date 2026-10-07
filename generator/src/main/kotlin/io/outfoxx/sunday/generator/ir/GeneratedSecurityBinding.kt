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

/** Application-owned provider and acquisition configuration for one logical wire-security scheme. */
data class GeneratedSecurityBinding(
  val provider: String? = null,
  val flow: Flow? = null,
  val discoveryUrl: String? = null,
  val authorizationUrl: String? = null,
  val tokenUrl: String? = null,
  val refreshUrl: String? = null,
  val audience: String? = null,
  val resource: String? = null,
  /** Explicit framework integration; absent preserves the existing application binding. */
  val quarkus: GeneratedQuarkusSecurityBinding? = null,
) {
  /** Supported acquisition contracts; interactive authorization and secrets stay with the application. */
  enum class Flow(
    /** Canonical source and runtime acquisition flow name. */
    val wireName: String,
  ) {
    CLIENT_CREDENTIALS("clientCredentials"),
    AUTHORIZATION_CODE("authorizationCode"),
    EXTERNAL("external"),
    STATIC("static"),
  }

  /** Applies a more-local declaration without treating missing members as removals. */
  fun merge(other: GeneratedSecurityBinding): GeneratedSecurityBinding =
    GeneratedSecurityBinding(
      provider = other.provider ?: provider,
      flow = other.flow ?: flow,
      discoveryUrl = other.discoveryUrl ?: discoveryUrl,
      authorizationUrl = other.authorizationUrl ?: authorizationUrl,
      tokenUrl = other.tokenUrl ?: tokenUrl,
      refreshUrl = other.refreshUrl ?: refreshUrl,
      audience = other.audience ?: audience,
      resource = other.resource ?: resource,
      quarkus = other.quarkus ?: quarkus,
    )
}
