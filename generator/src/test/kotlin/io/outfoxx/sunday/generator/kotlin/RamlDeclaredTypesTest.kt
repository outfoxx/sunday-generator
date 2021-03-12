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

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Declared Types Test")
class RamlDeclaredTypesTest {

  @Test
  fun `test multiple declarations with same name in separate files throws collision error`(
    @ResourceUri("raml/type-gen/types/decl-dups-fail.raml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in server mode to test collision detection
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val exception =
      assertThrows<IllegalStateException> {
        generateTypes(testUri, typeRegistry)
      }

    assertTrue(exception.message?.contains("Multiple classes") ?: false)
  }

  @Test
  fun `test multiple declarations with same name in separate files is fixed by package annotation`(
    @ResourceUri("raml/type-gen/types/decl-dups.raml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in client mode to allow generation
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import io.test.client.Test
        import kotlin.String

        public interface Test : Test {
          public val value2: String
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }
}
