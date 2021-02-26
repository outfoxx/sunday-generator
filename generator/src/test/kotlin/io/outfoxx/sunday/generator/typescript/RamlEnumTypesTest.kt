package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[TypeScript] [RAML] Enum Types Test")
class RamlEnumTypesTest {

  @Test
  fun `test names generated for enums types & values`(
    @ResourceUri("raml/type-gen/types/scalar/enums.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeModSpec = findTypeMod("TestEnum@!test-enum", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export enum TestEnum {
          None = 'none',
          Some = 'some',
          All = 'all'
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      }
    )
  }

}
