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

import io.outfoxx.sunday.generator.genError

/** Inclusive HTTP status interval; exact codes and families normalize to this representation. */
data class GeneratedHttpStatusRange(
  val first: Int,
  val last: Int = first,
) {
  init {
    require(first in 100..599 && last in first..599) { "HTTP status range must be ordered within 100..599" }
  }

  companion object {
    /** Reads an exact status, uppercase family (5XX), or inclusive range (500-599). */
    fun parse(
      value: Any?,
      location: String,
    ): GeneratedHttpStatusRange {
      val text = value?.toString().orEmpty()
      return when {
        value is Number && text.matches(Regex("[1-5][0-9]{2}")) -> GeneratedHttpStatusRange(text.toInt())
        value is String && text.matches(Regex("[1-5][0-9]{2}")) -> GeneratedHttpStatusRange(text.toInt())
        value is String && text.matches(Regex("[1-5]XX")) ->
          GeneratedHttpStatusRange(text.first().digitToInt() * 100, text.first().digitToInt() * 100 + 99)
        value is String && text.matches(Regex("[1-5][0-9]{2}-[1-5][0-9]{2}")) -> {
          val (first, last) = text.split('-').map(String::toInt)
          if (last < first) genError("$location HTTP status range must be ascending")
          GeneratedHttpStatusRange(first, last)
        }
        else -> genError("$location must be an HTTP status (100..599), family (1XX..5XX), or inclusive range (500-599)")
      }
    }
  }
}
