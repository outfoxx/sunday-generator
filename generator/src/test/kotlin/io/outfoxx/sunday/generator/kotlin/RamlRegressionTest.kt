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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Regression Test")
class RamlRegressionTest {

  @Test
  fun `test inherited inline enum reused`(
    @ResourceUri("raml/regression/inherited-inline-enum.raml") testUri: URI,
  ) {
    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val types = generateTypes(testUri, typeRegistry)

    val baseEnum = ClassName("io.test", "Base", "Priority")
    val childEnum = ClassName("io.test", "Child", "Priority")

    assertTrue(types.containsKey(baseEnum))
    assertFalse(types.containsKey(childEnum))
  }

  @Test
  fun `test external discriminator base properties retained`(
    @ResourceUri("raml/regression/external-discriminator-base-props.raml") testUri: URI,
  ) {
    val typeRegistry =
      KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel, JacksonAnnotations))

    val types = generateTypes(testUri, typeRegistry)

    val paramsType = findType("io.test.Container.Parameters", types)
    assertTrue(paramsType.propertySpecs.any { it.name == "version" })
  }
}
