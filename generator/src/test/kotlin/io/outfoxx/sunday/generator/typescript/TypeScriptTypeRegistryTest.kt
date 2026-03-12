/*
 * Copyright 2026 Outfox, Inc.
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

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.ImportStyle
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.utils.nullable
import io.outfoxx.sunday.generator.typescript.utils.undefinable
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.NULL
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER
import io.outfoxx.typescriptpoet.TypeName.Companion.SET
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
import io.outfoxx.typescriptpoet.TypeName.Companion.UNDEFINED
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class TypeScriptTypeRegistryTest {

  @Test
  fun `generateExportedTypeFiles merges types that share one module path`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val sharedModulePath = "!shared-module"

    typeRegistry.addServiceType(TypeName.namedImport("ServiceA", sharedModulePath), ClassSpec.builder("ServiceA"))
    typeRegistry.addServiceType(TypeName.namedImport("ServiceB", sharedModulePath), ClassSpec.builder("ServiceB"))

    val files = typeRegistry.generateExportedTypeFiles(setOf(GeneratedTypeCategory.Service))

    assertThat(files.size, equalTo(1))

    val output =
      buildString {
        files.single().writeTo(this)
      }
    assertThat(output, containsString("export class ServiceA"))
    assertThat(output, containsString("export class ServiceB"))
  }

  @Test
  fun `generateFiles uses normalized module paths and writes generation headers`() {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val outputDir = fs.getPath("/out")
    val typeRegistry = TypeScriptTypeRegistry(setOf(AddGenerationHeader), ImportStyle.NodeNext)
    val sharedModulePath = "!shared-module.js"

    typeRegistry.addServiceType(TypeName.namedImport("ServiceA", sharedModulePath), ClassSpec.builder("ServiceA"))
    typeRegistry.addServiceType(TypeName.namedImport("ServiceB", sharedModulePath), ClassSpec.builder("ServiceB"))

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Service), outputDir)

    val mergedFile = Files.readString(outputDir.resolve("shared-module.ts"))
    val indexFile = Files.readString(outputDir.resolve("index.ts"))

    assertThat(mergedFile, containsString("Generator: Sunday"))
    assertThat(mergedFile, containsString("shared-module.ts"))
    assertThat(indexFile, containsString("export * from './shared-module.js';"))
  }

  @Test
  fun `schema initializer uses direct refs for primitive types`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val schema = typeRegistry.schemaInitializer(STRING).toString()

    assertThat(schema, containsString("StringSchema"))
    assertThat(schema, not(containsString("runtime: SchemaRuntime")))
  }

  @Test
  fun `schema initializer supports nullable and optional set types`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val setType = TypeName.parameterizedType(SET, STRING).nullable.undefinable
    val schema = typeRegistry.schemaInitializer(setType).toString()

    assertThat(schema, containsString("defineSchema((runtime: SchemaRuntime) =>"))
    assertThat(schema, containsString("z.codec"))
    assertThat(schema, containsString(".nullable()"))
    assertThat(schema, containsString(".optional()"))
  }

  @Test
  fun `schema initializer falls back to unknown schema for unknown types and unions`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val unknownSchema = typeRegistry.schemaInitializer(TypeName.standard("MissingType")).toString()
    assertThat(unknownSchema, containsString("z.unknown()"))

    val singleChoiceUnion = typeRegistry.schemaInitializer(TypeName.unionType(STRING, NULL)).toString()
    assertThat(singleChoiceUnion, containsString("z.string()"))

    val multiChoiceUnion = typeRegistry.schemaInitializer(TypeName.unionType(STRING, NUMBER, NULL)).toString()
    assertThat(multiChoiceUnion, containsString("z.union(["))

    val optionalStringSchema = typeRegistry.schemaInitializer(TypeName.unionType(STRING, UNDEFINED)).toString()
    assertThat(optionalStringSchema, containsString(".optional()"))
  }

  @Test
  fun `externalDiscriminatedPropertySchema supports nullable and optional properties`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val propertyType = TypeName.standard("Payload").nullable.undefinable

    val helperMethod =
      typeRegistry.javaClass.getDeclaredMethod(
        "externalDiscriminatedPropertySchema",
        TypeName::class.java,
      )
    helperMethod.isAccessible = true
    val schema = helperMethod.invoke(typeRegistry, propertyType).toString()

    assertThat(schema, containsString("z.custom<Payload>()"))
    assertThat(schema, containsString(".nullable()"))
    assertThat(schema, containsString(".optional()"))
  }

  @Test
  fun `generateExportedTypeFiles sorts companion schema members deterministically by type name`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val modulePath = "models-schema"
    val parentType = TypeName.namedImport("Parent", "!models")
    val childType = TypeName.namedImport("Child", "!models")
    val alphaType = TypeName.namedImport("Alpha", "!models")

    val classSuperTypesField = typeRegistry.javaClass.getDeclaredField("classSuperTypes")
    classSuperTypesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val classSuperTypes =
      classSuperTypesField.get(typeRegistry) as MutableMap<TypeName.Standard, TypeName.Standard?>
    classSuperTypes[parentType] = null
    classSuperTypes[childType] = parentType
    classSuperTypes[alphaType] = null

    val companionSchemaFileMembersField = typeRegistry.javaClass.getDeclaredField("companionSchemaFileMembers")
    companionSchemaFileMembersField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val companionSchemaFileMembers =
      companionSchemaFileMembersField.get(typeRegistry) as
        MutableMap<String, MutableList<Pair<TypeName.Standard, CodeBlock>>>
    companionSchemaFileMembers[modulePath] =
      mutableListOf(
        parentType to CodeBlock.of("const parentSchema = true;\n"),
        alphaType to CodeBlock.of("const alphaSchema = true;\n"),
        childType to CodeBlock.of("const childSchema = true;\n"),
      )

    val generatedFiles = typeRegistry.generateExportedTypeFiles(setOf(GeneratedTypeCategory.Model))
    assertThat(generatedFiles.size, equalTo(1))

    val output =
      buildString {
        generatedFiles.single().writeTo(this)
      }

    val childIndex = output.indexOf("childSchema")
    val alphaIndex = output.indexOf("alphaSchema")
    val parentIndex = output.indexOf("parentSchema")
    assertTrue(childIndex >= 0 && alphaIndex >= 0 && parentIndex >= 0)
    assertTrue(alphaIndex < childIndex)
    assertTrue(childIndex < parentIndex)
  }

  @Test
  fun `importedType resolves implicit symbols from mapped imports`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeNameMappingsField = typeRegistry.javaClass.getDeclaredField("typeNameMappings")
    typeNameMappingsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val typeNameMappings = typeNameMappingsField.get(typeRegistry) as MutableMap<String, TypeName>
    typeNameMappings["shared"] = TypeName.namedImport("SharedType", "!shared-type")

    val importedTypeMethod = typeRegistry.javaClass.getDeclaredMethod("importedType", TypeName.Standard::class.java)
    importedTypeMethod.isAccessible = true

    val implicitShared = TypeName.standard(SymbolSpec.implicit("SharedType"))
    val imported = importedTypeMethod.invoke(typeRegistry, implicitShared) as SymbolSpec.Imported

    assertThat(imported.value, equalTo("SharedType"))
    assertThat(imported.source, equalTo("!shared-type"))
  }

  @Test
  fun `generatedTypeName keeps esm module paths extensionless`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf(), ImportStyle.ESM)

    val generated = typeRegistry.generatedTypeName("Service", "services/service")

    assertEquals("Service", generated.simpleName())
    assertEquals("!services/service", (generated.base as SymbolSpec.Imported).source)
  }

  @Test
  fun `generatedTypeName normalizes node-next module paths without doubling js extension`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf(), ImportStyle.NodeNext)

    val generated = typeRegistry.generatedTypeName("Service", "services/service.js")

    assertEquals("Service", generated.simpleName())
    assertEquals("!services/service.js", (generated.base as SymbolSpec.Imported).source)
  }
}
