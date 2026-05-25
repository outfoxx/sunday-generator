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

@file:OptIn(ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin.tools

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinSundayOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import java.net.URI

fun findType(
  name: String,
  types: Map<ClassName, TypeSpec>,
): TypeSpec = types[ClassName.bestGuess(name)] ?: fail("Type '$name' not defined")

fun generateSunday(
  uri: URI,
  typeRegistry: KotlinTypeRegistry,
  options: KotlinSundayOptions = kotlinSundayTestOptions,
): Map<ClassName, TypeSpec> {

  val result = TestAPIProcessing.process(uri)
  val api =
    RamlToGeneratedApi(options = GeneratedApiIrOptions(deriveServicesFromTags = options.servicesFromTags))
      .convert(result)

  KotlinSundayIrGenerator(api, typeRegistry, options)
    .generateServiceTypes()

  val builtTypes = typeRegistry.buildTypes()

  assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))

  return builtTypes
}

fun generateJaxrs(
  uri: URI,
  typeRegistry: KotlinTypeRegistry,
  options: KotlinJAXRSOptions = kotlinJAXRSTestOptions,
): Map<ClassName, TypeSpec> {

  val result = TestAPIProcessing.process(uri)
  val api =
    RamlToGeneratedApi(options = GeneratedApiIrOptions(deriveServicesFromTags = options.servicesFromTags))
      .convert(result)

  KotlinJAXRSIrGenerator(api, typeRegistry, options)
    .generateServiceTypes()

  val builtTypes = typeRegistry.buildTypes()

  assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))

  return builtTypes
}
