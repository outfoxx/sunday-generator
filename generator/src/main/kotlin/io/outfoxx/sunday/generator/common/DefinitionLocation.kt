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

import amf.client.model.domain.DomainElement
import amf.core.parser.Range
import io.outfoxx.sunday.generator.utils.annotations
import io.outfoxx.sunday.generator.utils.location

data class DefinitionLocation(val location: String, val range: Range) {

  constructor(element: DomainElement) : this(element.annotations.location, element.position())

  val errorMessage: String get() = "$location:${range.start().line()}:${range.start().column()})"

  override fun toString(): String {
    val start = range.start()
    val end = range.end()
    return "$location@(${start.line()}:${start.column()})-(${end.line()}:${end.column()})"
  }
}
