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

package io.outfoxx.sunday.generator.kotlin.tools

import amf.core.client.platform.model.document.Document
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.kotlin.KotlinGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinResolutionContext
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import java.net.URI

fun findType(name: String, types: Map<ClassName, TypeSpec>): TypeSpec =
  types[ClassName.bestGuess(name)] ?: fail("Type '$name' not defined")

fun generateTypes(uri: URI, typeRegistry: KotlinTypeRegistry): Map<ClassName, TypeSpec> {

  val (document, shapeIndex) = TestAPIProcessing.process(uri)

  val apiPackageName =
    document.encodes.findStringAnnotation(APIAnnotationName.KotlinPkg, typeRegistry.generationMode)
      ?: typeRegistry.defaultModelPackageName

  val apiTypeName = ClassName.bestGuess("$apiPackageName.API")
  typeRegistry.addServiceType(apiTypeName, TypeSpec.classBuilder(apiTypeName))

  TestAPIProcessing.generateTypes(document, shapeIndex, typeRegistry.generationMode, typeRegistry::defineProblemType) { name, schema ->
    val context = KotlinResolutionContext(document, shapeIndex, apiTypeName.nestedClass(name))
    typeRegistry.resolveTypeName(schema, context)
  }

  val builtTypes = typeRegistry.buildTypes()

  assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))

  return builtTypes
}

fun generate(
  uri: URI,
  typeRegistry: KotlinTypeRegistry,
  generatorFactory: (Document, ShapeIndex) -> KotlinGenerator
): Map<ClassName, TypeSpec> {

  val (document, shapeIndex) = TestAPIProcessing.process(uri)

  val generator = generatorFactory(document, shapeIndex)

  generator.generateServiceTypes()

  val builtTypes = typeRegistry.buildTypes()

  assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))

  return builtTypes
}
