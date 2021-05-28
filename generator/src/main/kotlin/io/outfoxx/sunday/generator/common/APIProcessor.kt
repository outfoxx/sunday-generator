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

import amf.MessageStyles
import amf.ProfileNames
import amf.client.AMF
import amf.client.environment.Environment
import amf.client.model.document.Document
import amf.client.parse.Raml10Parser
import amf.client.validate.ValidationReport
import amf.core.validation.SeverityLevels
import io.outfoxx.sunday.generator.utils.LocalResourceLoader
import java.net.URI
import java.util.concurrent.ExecutionException

open class APIProcessor {

  data class Result(
    val document: Document,
    val validatedDocument: Document,
    private val validationReport: ValidationReport
  ) {

    enum class Level {
      Error,
      Warning,
      Info
    }

    data class Entry(
      val level: Level,
      val file: String,
      val line: Int,
      val message: String
    )

    val isValid: Boolean
      get() =
        validationReport.conforms() &&
          validationReport.results().none { it.level() == SeverityLevels.VIOLATION() }

    val validationLog: List<Entry>
      get() = validationReport.results().map {
        val level =
          when (it.level()) {
            SeverityLevels.VIOLATION() -> Level.Error
            SeverityLevels.WARNING() -> Level.Warning
            SeverityLevels.INFO() -> Level.Info
            else -> Level.Error
          }
        val file = it.location()?.orElse("unknown")!!
        val line = it.position().start().line()
        val message = it.message()
        Entry(level, file, line, message)
      }
  }

  open fun process(uri: URI): Result {

    AMF.init().get()

    val environment = Environment().addClientLoader(LocalResourceLoader)

    val document =
      try {
        Raml10Parser(environment).parseFileAsync(uri.toString()).get() as Document
      } catch (x: ExecutionException) {
        throw x.cause!!
      }

    val validatedDocument = document.cloneUnit() as Document
    val validationReport = AMF.validate(validatedDocument, ProfileNames.RAML10(), MessageStyles.RAML()).get()

    return Result(document, validatedDocument, validationReport)
  }
}
