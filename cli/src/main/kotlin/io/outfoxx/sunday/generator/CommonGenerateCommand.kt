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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import java.net.URI

abstract class CommonGenerateCommand(
  name: String,
  val help: String,
) : CliktCommand(name = name) {

  override fun help(context: Context): String = help

  val serviceSuffix by option(
    "-service-suffix",
    help = "Suffix for generated services",
  ).default("API")

  val mediaTypes by option(
    "-media-type",
    help = "Specify order of default media types",
  ).multiple()
    .unique()

  val outputCategories by option(
    "-category",
    help = "Add category of type to output ${GeneratedTypeCategory.entries.joinToString { it.name }}",
  ).enum<GeneratedTypeCategory>()
    .multiple(GeneratedTypeCategory.entries)
    .unique()

  val outputDirectory by option(
    "-out",
    help = "Output directory",
  ).file(mustExist = true, canBeFile = false, canBeDir = true)
    .required()

  val problemBaseUri by option(
    "-problem-base",
    help = "Default problem base URI",
  ).default("http://example.com/")
    .validate { URI(it) }

  val files by argument(
    help = "Source files",
  ).file(mustExist = true, canBeFile = true, canBeDir = false)
    .multiple(required = true)
}
