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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateSunday
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] ModelInheritance Test")
@Tag("models")
class TypeScriptSundayIrModelInheritanceTest : TypeScriptSundayIrTestSupport() {

  @Test
  fun `generates inherited discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Parent@!parent", builtTypes))
          .writeTo(this)
      }
    val child1Output =
      buildString {
        FileSpec
          .get(findTypeMod("Child1@!child1", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlDiscriminatedTypesTest/simple.parent.ts", parentOutput)
    assertSnapshot("RamlDiscriminatedTypesTest/simple.sunday-ir.child1.ts", child1Output)
  }

  @Test
  fun `generates externally discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Parent@!parent", builtTypes))
          .writeTo(this)
      }
    val testOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Test@!test", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator.parent.ts", parentOutput)
    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator-sunday-ir.test.ts", testOutput)
  }

  @Test
  fun `public TypeScript Sunday generator owns inherited discriminated model generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes =
      generateSunday(testUri, typeRegistry, compiler, typeScriptSundayTestOptions)

    assertTrue(generatedTypes.containsKey(TypeName.standard("Parent@!parent")))
    assertTrue(generatedTypes.containsKey(TypeName.standard("Child1@!child1")))
    assertFalse(generatedTypes.containsKey(TypeName.standard("ParentSchema@!parent-schema")))
    assertFalse(generatedTypes.containsKey(TypeName.standard("Child1Schema@!child1-schema")))
  }
}
