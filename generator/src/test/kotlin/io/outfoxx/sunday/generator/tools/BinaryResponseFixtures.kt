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

package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import java.nio.file.Path

/** Shared download contract for compiled client parity tests. */
fun binaryResponseApi(frontend: String): GeneratedApi {
  val api =
    GeneratedApiIrExporter().export(
      Path.of("src/test/resources/binary-responses/downloads.${if (frontend == "raml") "raml" else "yaml"}").toUri(),
    )
  return api.copy(services = api.services.map { it.copy(name = "Downloads", group = null) })
}
