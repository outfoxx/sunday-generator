package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.SchemaMode
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class OasUnionTypesTest {

  @Test
  fun `test generated types for general union types`(
    @ResourceUri("openapi/type-gen/types/unions-general.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          public val any: kotlin.Any

          public val duplicate: kotlin.String
        
          public val nullable: kotlin.String?
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated types for common object types`(
    @ResourceUri("openapi/type-gen/types/unions-common-objects.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          public val value: io.test.Base
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated types for similarly named but uncommon object types`(
    @ResourceUri("openapi/type-gen/types/unions-uncommon-objects.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.lib.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          public val value: kotlin.Any
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }
}
