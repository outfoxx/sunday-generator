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

package io.outfoxx.sunday.generator.kotlin

import amf.core.client.platform.model.document.Document
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.common.ShapeIndex

class KotlinSundayGenerateCommand :
  KotlinGenerateCommand(name = "kotlin/sunday", help = "Generate Kotlin client for Sunday framework") {

  override val mode = GenerationMode.Client

  private val useResultResponseReturn by option(
    "-use-result-response-return",
    help = "Service methods will return results wrapped in a response",
  ).flag(default = false)

  override fun generatorFactory(document: Document, shapeIndex: ShapeIndex, typeRegistry: KotlinTypeRegistry) =
    KotlinSundayGenerator(
      document,
      shapeIndex,
      typeRegistry,
      KotlinSundayGenerator.Options(
        useResultResponseReturn,
        servicePackageName ?: packageName,
        problemBaseUri,
        mediaTypes.toList(),
        serviceSuffix,
      ),
    )
}
