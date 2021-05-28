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

package io.outfoxx.sunday.generator.typescript.tools

import amf.client.model.document.Document
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.typescript.TypeScriptGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptResolutionContext
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.typescriptpoet.AnyTypeSpec
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.net.URI

fun findTypeMod(name: String, types: Map<TypeName.Standard, ModuleSpec>): ModuleSpec =
  types[TypeName.standard(name)] ?: fail("Type '$name' not defined")

fun findNestedType(typeModSpec: ModuleSpec, vararg names: String): AnyTypeSpec? =
  typeModSpec.members.filterIsInstance<ModuleSpec>().firstOrNull { it.name == names.first() }
    ?.let { findNestedType(it, *names.dropLast(1).toTypedArray()) }
    ?: typeModSpec.members.filterIsInstance<AnyTypeSpec>().firstOrNull { it.name == names.first() }

fun generateTypes(
  uri: URI,
  typeRegistry: TypeScriptTypeRegistry,
  compiler: TypeScriptCompiler
): Map<TypeName.Standard, ModuleSpec> {

  val document = TestAPIProcessing.process(uri).document

  val apiTypeName = TypeName.namedImport("API", "!api")
  typeRegistry.addServiceType(apiTypeName, ClassSpec.builder(apiTypeName))

  TestAPIProcessing.generateTypes(document, Client, typeRegistry::defineProblemType) { name, schema ->
    val context = TypeScriptResolutionContext(document, apiTypeName.nested(name))
    typeRegistry.resolveTypeName(schema, context)
  }

  val builtTypes = typeRegistry.buildTypes()

  assertTrue(compileTypes(compiler, builtTypes))

  return builtTypes
}

fun generate(
  uri: URI,
  typeRegistry: TypeScriptTypeRegistry,
  compiler: TypeScriptCompiler,
  generatorFactory: (Document) -> TypeScriptGenerator
): Map<TypeName.Standard, ModuleSpec> {

  val document = TestAPIProcessing.process(uri).document

  val generator = generatorFactory(document)

  generator.generateServiceTypes()

  val builtTypes = typeRegistry.buildTypes()

  assertTrue(compileTypes(compiler, builtTypes))

  return builtTypes
}
