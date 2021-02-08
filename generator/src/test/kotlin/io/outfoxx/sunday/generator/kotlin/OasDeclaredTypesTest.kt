package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@Disabled
@ExtendWith(ResourceExtension::class)
class OasDeclaredTypesTest {

  @Test
  fun `test multiple declarations with same name in separate files throws collision error`(
    @ResourceUri("openapi/type-gen/types/decl-dups.yaml") testUri: URI
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
    @ResourceUri("openapi/type-gen/types/decl-dups.yaml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in client mode to allow generation
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val typeSpec = findType("io.test.client.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Test : io.test.Test {
          public val value: kotlin.String
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

}
