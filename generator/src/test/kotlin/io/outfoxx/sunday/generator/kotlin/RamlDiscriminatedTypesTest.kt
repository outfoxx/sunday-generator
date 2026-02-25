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
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Discriminated Types Test")
class RamlDiscriminatedTypesTest {

  @Test
  fun `test polymorphism added to generated interfaces of string discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Parent")]
        ?: error("Parent type is not defined")

    assertKotlinSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-interfaces-of-string-discriminated-types.output.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-interfaces-of-string-discriminated-types.output2.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-interfaces-of-string-discriminated-types.output3.kt",
      buildString {
        FileSpec
          .get("io.test", child2TypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test polymorphism added to generated classes of string discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Parent")]
        ?: error("Parent type is not defined")

    assertKotlinSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output2.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output3.kt",
      buildString {
        FileSpec
          .get("io.test", child2TypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test polymorphism added to generated interfaces of enum discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Parent")]
        ?: error("Parent type is not defined")

    assertKotlinSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-interfaces-of-enum-discriminated-types.output.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-interfaces-of-enum-discriminated-types.output2.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-interfaces-of-enum-discriminated-types.output3.kt",
      buildString {
        FileSpec
          .get("io.test", child2TypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test polymorphism added to generated classes of enum discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec =
      builtTypes[ClassName.bestGuess("io.test.Parent")]
        ?: error("Parent type is not defined")

    assertKotlinSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-enum-discriminated-types.output.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-enum-discriminated-types.output2.kt",
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
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-enum-discriminated-types.output3.kt",
      buildString {
        FileSpec
          .get("io.test", child2TypeSpec)
          .writeTo(this)
      },
    )
  }
}
