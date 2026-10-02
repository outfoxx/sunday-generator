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

import org.junit.jupiter.params.provider.Arguments

/** Shared frontend, namespace and target combinations for directional validation. */
internal object DirectionalToleranceArguments {
  @JvmStatic
  fun commonTargets(): List<Arguments> = targets().filter { it.get()[0] != "raml" }

  @JvmStatic
  fun targets(): List<Arguments> =
    listOf("raml", "openapi", "asyncapi", "composed").flatMap { source ->
      listOf("javax", "jakarta").flatMap { namespace ->
        listOf("sunday", "client", "server").map { target -> Arguments.of(source, namespace, target) }
      }
    }
}
