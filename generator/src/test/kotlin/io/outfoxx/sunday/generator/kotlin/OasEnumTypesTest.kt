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
class OasEnumTypesTest {

  @Test
  fun `test names generated for enums types & values`(
    @ResourceUri("openapi/type-gen/types/scalar/enums.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.TestEnum", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public enum class TestEnum {
          None,
          Some,
          All,
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

}
