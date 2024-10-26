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

package io.outfoxx.sunday.generator.common

import amf.apicontract.client.platform.RAMLConfiguration
import amf.core.client.common.transform.PipelineId
import amf.core.client.common.validation.SeverityLevels
import amf.core.client.platform.model.document.Document
import amf.core.client.platform.validation.AMFValidationResult
import io.outfoxx.sunday.generator.utils.LocalSundayDefinitionResoureceLoader
import java.net.URI
import java.util.concurrent.ExecutionException

open class APIProcessor {

  data class Result(
    val document: Document,
    val shapeIndex: ShapeIndex,
    private val validationResults: List<AMFValidationResult>,
  ) {

    enum class Level {
      Error,
      Warning,
      Info,
    }

    data class Entry(
      val level: Level,
      val file: String,
      val line: Int,
      val colum: Int,
      val message: String,
    )

    val isValid: Boolean
      get() = validationResults.none { it.severityLevel() == SeverityLevels.VIOLATION() }

    val validationLog: List<Entry>
      get() = validationResults.map {
        val level =
          when (it.severityLevel()) {
            SeverityLevels.VIOLATION() -> Level.Error
            SeverityLevels.WARNING() -> Level.Warning
            SeverityLevels.INFO() -> Level.Info
            else -> Level.Error
          }
        val file = it.location()?.orElse("unknown")!!
        val line = it.position().start().line()
        val column = it.position().start().column()
        val message = it.message()
        Entry(level, file, line, column, message)
      }
  }

  open fun process(uri: URI): Result {

    val ramlClient =
      RAMLConfiguration.RAML10()
        .withResourceLoader(LocalSundayDefinitionResoureceLoader)
        .baseUnitClient()

    val (unresolvedDocument, validationResults) =
      try {
        val result = ramlClient.parseDocument(uri.toString()).get()
        result.document() to result.results()
      } catch (x: ExecutionException) {
        throw x.cause ?: x
      }

    val shapeIndex = ShapeIndex.builder().index(unresolvedDocument).build()

    val validationReport = ramlClient.validate(unresolvedDocument).get()

    val resolvedDocument = ramlClient.transform(unresolvedDocument, PipelineId.Cache()).baseUnit() as Document

    return Result(resolvedDocument, shapeIndex, validationResults + validationReport.results())
  }
}
