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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedService

/** Generic endpoint access policy; authentication mechanisms and roles remain application configuration. */
enum class GeneratedEndpointAccess {
  AUTHENTICATED,
  PUBLIC,
  UNSPECIFIED,
}

/** Resolves anonymous alternatives and inherited security without interpreting schemes as roles or mechanisms. */
fun GeneratedApi.endpointAuthentication(
  service: GeneratedService,
  operation: GeneratedOperation,
): GeneratedEndpointAccess {
  val auth = effectiveAuth(service, operation) ?: return GeneratedEndpointAccess.UNSPECIFIED
  return when {
    auth.requirements.any { it.schemes.isEmpty() } -> GeneratedEndpointAccess.PUBLIC
    auth.requirements.isNotEmpty() || auth.schemes.isNotEmpty() -> GeneratedEndpointAccess.AUTHENTICATED
    auth.securityOverride -> GeneratedEndpointAccess.PUBLIC
    else -> GeneratedEndpointAccess.UNSPECIFIED
  }
}
