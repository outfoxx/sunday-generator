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

package io.outfoxx.sunday.generator

import amf.core.client.platform.model.domain.DomainElement
import io.outfoxx.sunday.generator.utils.location

class GenerationException(
  message: String,
  val file: String,
  val line: Int,
  val column: Int,
) : Exception(message) {

  override fun toString(): String = "$file:$line: $message"
}

fun genError(message: String, element: DomainElement? = null): Nothing {
  if (element == null) {
    throw GenerationException(message, "", 0, 0)
  }

  throw GenerationException(
    message,
    element.annotations().location,
    element.position().start().line(),
    element.position().start().column(),
  )
}
