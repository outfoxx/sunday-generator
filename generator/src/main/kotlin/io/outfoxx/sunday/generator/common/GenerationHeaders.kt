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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object GenerationHeaders {

  private val paramRegex = """\{\{\s*(\w+)\s*}}""".toRegex()

  fun create(
    fileName: String,
    linePrefix: String = "",
    generationTimestamp: String? = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
  ): String =
    GenerationHeaders::class.java.classLoader
      .getResourceAsStream("header.gen.txt")!!
      .readAllBytes()
      .decodeToString()
      .split("\n")
      .mapNotNull { line ->
        if (generationTimestamp.isNullOrBlank() && line.contains("{{ date }}")) {
          null
        } else {
          line
        }
      }.joinToString("\n") { line ->
        val replaced =
          line.replace(paramRegex) {
            when (it.groupValues[1]) {
              "filename" -> fileName
              "date" -> generationTimestamp ?: ""
              else -> ""
            }
          }
        "$linePrefix$replaced"
      }
}
