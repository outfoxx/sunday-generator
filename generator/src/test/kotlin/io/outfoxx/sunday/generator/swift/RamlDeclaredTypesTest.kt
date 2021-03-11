package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.SwiftCompilerExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift] [RAML] Declared Types Test")
class RamlDeclaredTypesTest {

  @Test
  fun `test multiple declarations with same name in separate files throws collision error`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/decl-dups-fail.raml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in server mode to test collision detection
    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val exception =
      assertThrows<IllegalStateException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("Multiple classes") ?: false)
  }

}
