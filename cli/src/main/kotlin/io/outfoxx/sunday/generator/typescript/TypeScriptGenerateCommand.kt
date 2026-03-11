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

package io.outfoxx.sunday.generator.typescript

import amf.core.client.platform.model.document.Document
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.CommonGenerateCommand
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.flags
import io.outfoxx.sunday.generator.grouped
import io.outfoxx.sunday.generator.provideDelegate
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.ImportStyle
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader

abstract class TypeScriptGenerateCommand(
  name: String,
  help: String,
) : CommonGenerateCommand(name = name, help = help) {

  val options by flags<TypeScriptTypeRegistry.Option> {
    AddGenerationHeader to "Add generation header to generated files".default(true)
  }.grouped("Model Generation Options")

  val importStyle by option(
    "-import-style",
    help = "TypeScript import style (esm or node-next)",
  ).enum<ImportStyle> {
    when (it) {
      ImportStyle.ESM -> "esm"
      ImportStyle.NodeNext -> "node-next"
    }
  }.default(ImportStyle.ESM)

  override val typeRegistry: TypeScriptTypeRegistry by lazy {
    TypeScriptTypeRegistry(options, importStyle)
  }

  override fun generatorFactory(
    document: Document,
    shapeIndex: ShapeIndex,
  ) = generatorFactory(document, shapeIndex, typeRegistry)

  abstract fun generatorFactory(
    document: Document,
    shapeIndex: ShapeIndex,
    typeRegistry: TypeScriptTypeRegistry,
  ): TypeScriptGenerator
}
