package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Swift] [RAML] Enum Types Test")
class RamlEnumTypesTest {

  @Test
  fun `test names generated for enums types & values`(
    @ResourceUri("raml/type-gen/types/scalar/enums.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("TestEnum", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public enum TestEnum : String, CaseIterable, Codable {

          case none = "none"
          case some = "some"
          case all = "all"
          case snakeCase = "snake_case"
          case kebabCase = "kebab-case"

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

}
