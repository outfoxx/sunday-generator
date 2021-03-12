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

import amf.client.model.document.Document
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.CommonGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase

abstract class TypeScriptGenerateCommand(name: String, help: String) : CommonGenerateCommand(name = name, help = help) {

  companion object {

    val defaultOptions = setOf(
      JacksonDecorators,
      AddGenerationHeader,
    )
  }

  val problemBaseUri
    by option(
    "-problem-base",
    help = "Default problem base URI"
  ).default("http://example.com/")

  val enabledOptions
    by option(
    "-enable",
    help = "Enable type generation option"
  ).enum<TypeScriptTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
    .multiple()

  val disabledOptions
    by option(
    "-disable",
    help = "Disable type generation option"
  ).enum<TypeScriptTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
    .multiple()

  val options get() = defaultOptions.plus(enabledOptions).minus(disabledOptions)

  override val typeRegistry: TypeScriptTypeRegistry by lazy {
    TypeScriptTypeRegistry(options)
  }

  override fun generatorFactory(document: Document) = generatorFactory(document, typeRegistry)

  abstract fun generatorFactory(document: Document, typeRegistry: TypeScriptTypeRegistry): TypeScriptGenerator
}
