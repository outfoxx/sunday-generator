package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Union Types Test")
class RamlUnionTypesTest {

  @Test
  fun `test generated types for general union types`(
    @ResourceUri("raml/type-gen/types/unions-general.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.String

        public interface Test {
          public val any: Any

          public val duplicate: String
        
          public val nullable: String?
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated types for common object types`(
    @ResourceUri("raml/type-gen/types/unions-common-objects.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        public interface Test {
          public val value: Base
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated types for similarly named but uncommon object types`(
    @ResourceUri("raml/type-gen/types/unions-uncommon-objects.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.lib.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.Any

        public interface Test {
          public val value: Any
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }
}
