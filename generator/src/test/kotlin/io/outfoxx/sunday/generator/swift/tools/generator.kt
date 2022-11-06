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

import amf.core.client.platform.model.document.Document
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.swift.SwiftGenerator
import io.outfoxx.sunday.generator.swift.SwiftResolutionContext
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.TypeSpec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.net.URI

fun findType(name: String, types: Map<DeclaredTypeName, TypeSpec>): TypeSpec =
  types[DeclaredTypeName.typeName(".$name")] ?: fail("Type '$name' not defined")

fun generateTypes(
  uri: URI,
  typeRegistry: SwiftTypeRegistry,
  compiler: SwiftCompiler
): Map<DeclaredTypeName, TypeSpec> {

  val (document, shapeIndex) = TestAPIProcessing.process(uri)

  val apiModuleName =
    document.encodes.findStringAnnotation(APIAnnotationName.SwiftModule, null)
      ?: ""

  val apiTypeName = DeclaredTypeName.typeName("$apiModuleName.API")
  typeRegistry.addServiceType(apiTypeName, TypeSpec.classBuilder(apiTypeName))

  TestAPIProcessing.generateTypes(document, shapeIndex, Client, typeRegistry::defineProblemType) { name, shape ->
    val context = SwiftResolutionContext(document, shapeIndex, apiTypeName.nestedType(name))
    typeRegistry.resolveTypeName(shape, context)
  }

  val builtTypes =
    typeRegistry.buildTypes()
      .filter { it.key.enclosingTypeName() == null }

  assertTrue(compileTypes(compiler, builtTypes))

  return builtTypes
}

fun generate(
  uri: URI,
  typeRegistry: SwiftTypeRegistry,
  compiler: SwiftCompiler,
  generatorFactory: (Document, ShapeIndex) -> SwiftGenerator
): Map<DeclaredTypeName, TypeSpec> {

  val (document, shapeIndex) = TestAPIProcessing.process(uri)

  val generator = generatorFactory(document, shapeIndex)

  generator.generateServiceTypes()

  val builtTypes =
    typeRegistry.buildTypes()
      .filter { it.key.enclosingTypeName() == null }

  assertTrue(compileTypes(compiler, builtTypes))

  return builtTypes
}
