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

package io.outfoxx.sunday.generator.swift

import amf.core.client.platform.model.document.Document
import io.outfoxx.sunday.generator.CommonGenerateCommand
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.flags
import io.outfoxx.sunday.generator.grouped
import io.outfoxx.sunday.generator.provideDelegate
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.Option.AddGeneratedHeader
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.Option.DefaultIdentifiableTypes

abstract class SwiftGenerateCommand(name: String, help: String) : CommonGenerateCommand(name = name, help = help) {

  val options by flags<SwiftTypeRegistry.Option> {
    AddGeneratedHeader to "Add generated header to generated files".default(true)
    DefaultIdentifiableTypes to "Conform any types with an `id` parameter to `Identifiable`".default(true)
  }.grouped("Model Generation Options")

  override val typeRegistry: SwiftTypeRegistry by lazy {
    SwiftTypeRegistry(options)
  }

  override fun generatorFactory(document: Document, shapeIndex: ShapeIndex) =
    generatorFactory(document, shapeIndex, typeRegistry)

  abstract fun generatorFactory(
    document: Document,
    shapeIndex: ShapeIndex,
    typeRegistry: SwiftTypeRegistry,
  ): SwiftGenerator
}
