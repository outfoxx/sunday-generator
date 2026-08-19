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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedService

internal fun PythonGeneratorOptions.requireHttpOnly() {
  if (broker) {
    genError("Python broker generation is not supported; generate HTTP services without -broker")
  }
}

internal fun GeneratedApi.pythonHttpServices(): List<GeneratedService> =
  services.mapNotNull { service ->
    service
      .copy(operations = service.operations.filter { operation -> operation.isPythonHttpOperation() })
      .takeIf { filtered -> filtered.operations.isNotEmpty() }
  }

private fun GeneratedOperation.isPythonHttpOperation(): Boolean =
  method.uppercase() !in asyncApiOperationMethods ||
    (path.startsWith("/") && !hasNonHttpProtocolBinding())

private fun GeneratedOperation.hasNonHttpProtocolBinding(): Boolean =
  protocol
    ?.bindings
    .orEmpty()
    .any { binding -> !binding.protocol.isHttpProtocol() }

private fun String.isHttpProtocol(): Boolean = equals("http", ignoreCase = true) || equals("https", ignoreCase = true)

private val asyncApiOperationMethods = setOf("PUBLISH", "SUBSCRIBE")
