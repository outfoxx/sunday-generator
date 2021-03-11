package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.TypeScriptCompilerExtension
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, TypeScriptCompilerExtension::class)
@DisplayName("[TypeScript] [RAML] Declared Types Test")
class RamlDeclaredTypesTest {

  @Test
  fun `test multiple declarations with same name in separate files throws collision error`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/decl-dups-fail.raml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in server mode to test collision detection
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val exception =
      assertThrows<IllegalStateException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("Multiple classes") ?: false)
  }

  @Test
  fun `test multiple declarations with same name in separate files is fixed by package annotation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/decl-dups.raml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in client mode to allow generation
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {Test as Test_} from './test/client/test';
        
        
        export interface Test extends Test_ {

          value2: string;

        }
        
        export class Test extends Test_ implements Test {

          value2: string;

          constructor(value: string, value2: string) {
            super(value);
            this.value2 = value2;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.value ?? this.value, src.value2 ?? this.value2);
          }

          toString(): string {
            return `Test(value='${'$'}{this.value}', value2='${'$'}{this.value2}')`;
          }

        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      }
    )
  }

}
