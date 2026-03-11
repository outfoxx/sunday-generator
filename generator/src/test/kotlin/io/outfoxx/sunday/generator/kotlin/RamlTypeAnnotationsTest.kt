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

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asTypeName
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI
import java.time.Instant

@KotlinTest
@DisplayName("[Kotlin] [RAML] Type Annotations Test")
class RamlTypeAnnotationsTest {

  companion object {

    private val resourceClassLoader = Thread.currentThread().contextClassLoader
  }

  @ParameterizedTest(name = "{0} in {1} mode")
  @CsvSource(
    "kotlinModelPackage,            Server,   +io.explicit.test",
    "kotlinModelPackage,            Client,   +io.explicit.test",
    "kotlinModelPackage:server,     Server,   +io.explicit.test.server",
    "kotlinModelPackage:server,     Client,   +io.explicit.test",
    "kotlinModelPackage:client,     Server,   +io.explicit.test",
    "kotlinModelPackage:client,     Client,   +io.explicit.test.client",
    "kotlinPackage,                 Server,   +io.explicit.test",
    "kotlinPackage,                 Client,   +io.explicit.test",
    "kotlinPackage:server,          Server,   +io.explicit.test.server",
    "kotlinPackage:server,          Client,   +io.explicit.test",
    "kotlinPackage:client,          Server,   +io.explicit.test",
    "kotlinPackage:client,          Client,   +io.explicit.test.client",
    "kotlinType,                    Server,   ~java.time.LocalDateTime",
    "kotlinType,                    Client,   ~java.time.LocalDateTime",
    "kotlinType:server,             Server,   ~java.time.Instant",
    "kotlinType:server,             Client,   ~java.time.LocalDateTime",
    "kotlinType:client,             Server,   ~java.time.Instant",
    "kotlinType:client,             Client,   ~java.time.LocalDateTime",
  )
  fun `test type annotations`(
    annotationName: String,
    mode: GenerationMode,
    expectedPackageName: String,
  ) {

    val testAnnName = annotationName.split("""(?=[A-Z])|:""".toRegex()).joinToString("-") { it.lowercase() }
    val testRamlFile = "raml/type-gen/annotations/type-$testAnnName.raml"
    val testUri =
      resourceClassLoader.getResource(testRamlFile)?.toURI()
        ?: fail("unable to find test RAML file: $testRamlFile")

    val typeRegistry = KotlinTypeRegistry("io.test", null, mode, setOf())

    val builtTypes =
      generateTypes(testUri, typeRegistry)
        .filterNot { it.key.simpleName == "API" }

    when (expectedPackageName[0]) {

      '+' ->
        assertEquals(
          expectedPackageName.substring(1),
          builtTypes.entries
            .first()
            .key.packageName,
        )

      '~' ->
        assertEquals(
          expectedPackageName.substring(1),
          builtTypes.entries
            .first()
            .value.propertySpecs
            .firstOrNull()
            ?.type
            ?.toString(),
        )
    }
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation`(
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Group", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation (dashed scheme)`(
    @ResourceUri("raml/type-gen/annotations/type-nested-dashed.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Group", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation-dashed-scheme.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using library types`(
    @ResourceUri("raml/type-gen/annotations/type-nested-lib.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Root", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation-using-library-types.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using only library types`(
    @ResourceUri("raml/type-gen/annotations/type-nested-lib2.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Root", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation-using-only-library-types.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class generated kotlin implementations`(
    @ResourceUri("raml/type-gen/annotations/type-kotlin-impl.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-generated-kotlin-implementations.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated types`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Parent")]
        ?: error("Parent type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output.kt",
      buildString {
        FileSpec
          .get("io.test", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Child1")]
        ?: error("Child1 type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output2.kt",
      buildString {
        FileSpec
          .get("io.test", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Child2")]
        ?: error("Child2 type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output3.kt",
      buildString {
        FileSpec
          .get("io.test", child2TypeSpec)
          .writeTo(this)
      },
    )

    val testTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Test")]
        ?: error("Test type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output4.kt",
      buildString {
        FileSpec
          .get("io.test", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated enum types`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-enum.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Parent")]
        ?: error("Parent type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output.kt",
      buildString {
        FileSpec
          .get("io.test", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Child1")]
        ?: error("Child1 type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output2.kt",
      buildString {
        FileSpec
          .get("io.test", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Child2")]
        ?: error("Child2 type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output3.kt",
      buildString {
        FileSpec
          .get("io.test", child2TypeSpec)
          .writeTo(this)
      },
    )

    val testTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Test")]
        ?: error("Test type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output4.kt",
      buildString {
        FileSpec
          .get("io.test", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated types with no discriminator property`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-no-property.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Parent")]
        ?: error("Parent type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types-with-no-discriminator-property.output.kt",
      buildString {
        FileSpec
          .get("io.test", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Child1")]
        ?: error("Child1 type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types-with-no-discriminator-property.output2.kt",
      buildString {
        FileSpec
          .get("io.test", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Child2")]
        ?: error("Child2 type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types-with-no-discriminator-property.output3.kt",
      buildString {
        FileSpec
          .get("io.test", child2TypeSpec)
          .writeTo(this)
      },
    )

    val testTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Test")]
        ?: error("Test type is not defined")

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types-with-no-discriminator-property.output4.kt",
      buildString {
        FileSpec
          .get("io.test", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test external discriminator must exist`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry)
      }

    assertTrue(exception.message?.contains("External discriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel))

    val generatedTypes = generateTypes(testUri, typeRegistry)
    val typeSpec = findType("io.test.Test", generatedTypes)

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-patchable-class-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Child", generatedTypes)

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-patchable-class-generation.output2.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec2)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test discriminated patchable class generation`(
    @ResourceUri("raml/type-gen/annotations/type-patchable-disc.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val generatedTypes = generateTypes(testUri, typeRegistry)
    val typeSpec = findType("io.test.Test", generatedTypes)

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-discriminated-patchable-class-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Child", generatedTypes)

    assertKotlinSnapshot(
      "RamlTypeAnnotationsTest/test-discriminated-patchable-class-generation.output2.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec2)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test types can be generated in one mode and overridden in another`(
    @ResourceUri("raml/type-gen/annotations/type-kotlin-type-dual.raml") testUri: URI,
  ) {
    val valueTypeName = ClassName.bestGuess("io.test.Value")

    val serverTypeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())
    val serverTypes = generateTypes(testUri, serverTypeRegistry)

    val serverTypeSpec = findType("io.test.Test", serverTypes)

    val clientTypeRegistry = KotlinTypeRegistry("io.test", null, Client, setOf())
    val clientTypes = generateTypes(testUri, clientTypeRegistry)
    val clientTypeSpec = findType("io.test.Test", clientTypes)

    assertEquals(serverTypeSpec.propertySpecs.getOrNull(0)?.type, Instant::class.asTypeName())
    assertNull(serverTypes[valueTypeName])

    assertEquals(clientTypeSpec.propertySpecs.getOrNull(0)?.type, ClassName.bestGuess("io.test.Value"))
    assertNotNull(clientTypes[valueTypeName])
  }
}
