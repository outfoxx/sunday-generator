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

import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Discriminated Types Test")
class RamlDiscriminatedTypesTest {

  @Test
  fun `test polymorphism added to generated classes of string discriminated types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val parentTypeModSpec = findTypeMod("Parent@!parent", generatedTypes)
    val parentOutput =
      buildString {
        FileSpec
          .get(parentTypeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlDiscriminatedTypesTest/simple.parent.ts", parentOutput)
    assertFalse(parentOutput.contains("export const ParentSchema"))

    val parentSchemaTypeModSpec = findTypeMod("ParentSchema@!parent-schema", generatedTypes)
    val parentSchemaOutput =
      buildString {
        FileSpec
          .get(parentSchemaTypeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlDiscriminatedTypesTest/simple.parent-schema.ts", parentSchemaOutput)

    val child1TypeModSpec = findTypeMod("Child1@!child1", generatedTypes)
    val child1Output =
      buildString {
        FileSpec
          .get(child1TypeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlDiscriminatedTypesTest/simple.child1.ts", child1Output)
    assertFalse(child1Output.contains("export const Child1Schema"))

    val child1SchemaTypeModSpec = findTypeMod("Child1Schema@!child1-schema", generatedTypes)
    val child1SchemaOutput =
      buildString {
        FileSpec
          .get(child1SchemaTypeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlDiscriminatedTypesTest/simple.child1-schema.ts", child1SchemaOutput)
  }

  @Test
  fun `test polymorphism added to generated classes of enum discriminated types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val parentTypeModSpec = findTypeMod("Parent@!parent", generatedTypes)
    val parentOutput =
      buildString {
        FileSpec
          .get(parentTypeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlDiscriminatedTypesTest/enum.parent.ts", parentOutput)
    assertFalse(parentOutput.contains("export const ParentSchema"))

    val parentSchemaTypeModSpec = findTypeMod("ParentSchema@!parent-schema", generatedTypes)
    val parentSchemaOutput =
      buildString {
        FileSpec
          .get(parentSchemaTypeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlDiscriminatedTypesTest/enum.parent-schema.ts", parentSchemaOutput)
  }
}
