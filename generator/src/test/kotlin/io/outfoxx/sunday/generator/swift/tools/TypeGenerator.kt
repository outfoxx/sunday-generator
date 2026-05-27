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

package io.outfoxx.sunday.generator.swift.tools

import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftSundayOptions
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.TypeSpec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.net.URI

fun findType(
  name: String,
  types: Map<DeclaredTypeName, TypeSpec>,
): TypeSpec = types[DeclaredTypeName.typeName(".$name")] ?: fail("Type '$name' not defined")

fun generateSunday(
  uri: URI,
  typeRegistry: SwiftTypeRegistry,
  compiler: SwiftCompiler,
  options: SwiftSundayOptions = swiftSundayTestOptions,
): Map<DeclaredTypeName, TypeSpec> {

  val result = TestAPIProcessing.process(uri)
  val api =
    RamlToGeneratedApi(options = GeneratedApiIrOptions(deriveServicesFromTags = options.servicesFromTags))
      .convert(result)

  SwiftSundayIrGenerator(api, typeRegistry, options)
    .generateServiceTypes()

  val builtTypes =
    typeRegistry
      .buildTypes()
      .filter { it.key.enclosingTypeName() == null }

  assertTrue(compileTypes(compiler, builtTypes))

  return builtTypes
}
