package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI

@ExtendWith(ResourceExtension::class)
class RamlTypeAnnotationsTest {

  companion object {

    private val resourceClassLoader = Thread.currentThread().contextClassLoader
  }

  @ParameterizedTest(name = "{0} in {1} mode")
  @CsvSource(
    "javaModelPackage,              Server,   +io.explicit.test",
    "javaModelPackage,              Client,   +io.explicit.test",
    "javaModelPackage:server,       Server,   +io.explicit.test.server",
    "javaModelPackage:server,       Client,   +io.explicit.test",
    "javaModelPackage:client,       Server,   +io.explicit.test",
    "javaModelPackage:client,       Client,   +io.explicit.test.client",
    "javaPackage,                   Server,   +io.explicit.test",
    "javaPackage,                   Client,   +io.explicit.test",
    "javaPackage:server,            Server,   +io.explicit.test.server",
    "javaPackage:server,            Client,   +io.explicit.test",
    "javaPackage:client,            Server,   +io.explicit.test",
    "javaPackage:client,            Client,   +io.explicit.test.client",
    "kotlinType,                    Server,   ~java.time.LocalDateTime",
    "kotlinType,                    Client,   ~java.time.LocalDateTime",
    "kotlinType:server,             Server,   ~java.time.Instant",
    "kotlinType:server,             Client,   ~java.time.LocalDateTime",
    "kotlinType:client,             Server,   ~java.time.Instant",
    "kotlinType:client,             Client,   ~java.time.LocalDateTime"
  )
  fun `test type annotations`(
    annotationName: String,
    mode: GenerationMode,
    expectedPackageName: String
  ) {

    val testAnnName = annotationName.split("""(?=[A-Z])|:""".toRegex()).joinToString("-") { it.toLowerCase() }
    val testRamlFile = "raml/type-gen/annotations/type-${testAnnName}.raml"
    val testUri = resourceClassLoader.getResource(testRamlFile)?.toURI()
      ?: fail("unable to find test RAML file: $testRamlFile")

    val typeRegistry = KotlinTypeRegistry("io.test", mode, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry)

    when (expectedPackageName[0]) {

      '+' ->
        assertEquals(
          expectedPackageName.substring(1),
          builtTypes.entries.first().key.packageName
        )

      '~' ->
        assertEquals(
          expectedPackageName.substring(1),
          builtTypes.entries.first().value.propertySpecs.firstOrNull()?.type?.toString()
        )
    }
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation`(
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf())

    val typeSpec = findType("io.test.Group", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Group {
          public val value: kotlin.String

          public interface Member1 : io.test.Group {
            public val memberValue1: kotlin.String

            public interface Sub : io.test.Group.Member1 {
              public val subMemberValue: kotlin.String
            }
          }
        
          public interface Member2 : io.test.Group {
            public val memberValue2: kotlin.String
          }
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using library types`(
    @ResourceUri("raml/type-gen/annotations/type-nested-lib.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf())

    val typeSpec = findType("io.test.Root", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Root {
          public val value: kotlin.String

          public interface Group {
            public val value: kotlin.String
  
            public interface Member {
              public val memberValue: kotlin.String
            }
          }
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

  @Test
  fun `test class generated kotlin implementations`(
    @ResourceUri("raml/type-gen/annotations/type-kotlin-impl.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public class Test {
          @get:com.fasterxml.jackson.`annotation`.JsonIgnore
          public val className: kotlin.String
            get() = java.time.LocalDateTime::class.qualifiedName + "-value-" + "-literal"

          public fun copy() = io.test.Test()
        
          public override fun toString() = ${'"'}""Test()""${'"'}
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

  @Test
  fun `test class hierarchy generated for externally discriminated types`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec = builtTypes[ClassName.bestGuess("io.test.Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        @com.fasterxml.jackson.`annotation`.JsonSubTypes(value = [
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "Child1", value = io.test.Child1::class),
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "child2", value = io.test.Child2::class)
        ])
        public abstract class Parent {
          @get:com.fasterxml.jackson.`annotation`.JsonIgnore
          public abstract val type: kotlin.String
        
          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            return true
          }

          public override fun toString() = ${'"'}""Parent()""${'"'}
        }
        
      """.trimIndent(),
      parenTypeSpec.toString()
    )

    val child1TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        public class Child1(
          public val value: kotlin.String?
        ) : io.test.Parent() {
          public override val type: kotlin.String
            get() = "Child1"

          public fun copy(value: kotlin.String?) = io.test.Child1(value ?: this.value)

          public override fun hashCode(): kotlin.Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Child1

            if (!super.equals(other)) return false
            if (value != other.value) return false

            return true
          }

          public override fun toString() = ${'"'}""Child1(value='${'$'}value')""${'"'}
        }
        
      """.trimIndent(),
      child1TypeSpec.toString()
    )

    val child2TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        public class Child2(
          public val value: kotlin.String?
        ) : io.test.Parent() {
          public override val type: kotlin.String
            get() = "child2"

          public fun copy(value: kotlin.String?) = io.test.Child2(value ?: this.value)
        
          public override fun hashCode(): kotlin.Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }
        
          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as io.test.Child2
        
            if (!super.equals(other)) return false
            if (value != other.value) return false
        
            return true
          }
        
          public override fun toString() = ${'"'}""Child2(value='${'$'}value')""${'"'}
        }
        
      """.trimIndent(),
      child2TypeSpec.toString()
    )

    val testTypeSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
      ?: error("Test type is not defined")

    assertEquals(
      """
        public class Test(
          @com.fasterxml.jackson.`annotation`.JsonTypeInfo(
            use = com.fasterxml.jackson.`annotation`.JsonTypeInfo.Id.NAME,
            include = com.fasterxml.jackson.`annotation`.JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "parentType"
          )
          public val parent: io.test.Parent,
          public val parentType: kotlin.String
        ) {
          public fun copy(parent: io.test.Parent?, parentType: kotlin.String?) = io.test.Test(parent ?: this.parent, parentType ?: this.parentType)
        
          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + parent.hashCode()
            result = 31 * result + parentType.hashCode()
            return result
          }
        
          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as io.test.Test
        
            if (parent != other.parent) return false
            if (parentType != other.parentType) return false
        
            return true
          }
        
          public override fun toString() = ""${'"'}
          |Test(parent='${'$'}parent',
          | parentType='${'$'}parentType')
          ${'"'}"".trimMargin()
        }
        
      """.trimIndent(),
      testTypeSpec.toString()
    )

  }

  @Test
  fun `test external discriminator must exist`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ImplementModel, JacksonAnnotations))

    val exception =
      assertThrows<IllegalStateException> {
        generateTypes(testUri, typeRegistry)
      }

    assertTrue(exception.message?.contains("External discriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public class Test(
          public val string: kotlin.String,
          public val int: kotlin.Int,
          public val bool: kotlin.Boolean
        ) {
          public fun copy(
            string: kotlin.String?,
            int: kotlin.Int?,
            bool: kotlin.Boolean?
          ) = io.test.Test(string ?: this.string, int ?: this.int, bool ?: this.bool)

          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + string.hashCode()
            result = 31 * result + int.hashCode()
            result = 31 * result + bool.hashCode()
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test

            if (string != other.string) return false
            if (int != other.int) return false
            if (bool != other.bool) return false

            return true
          }

          public override fun toString() = ""${'"'}
          |Test(string='${'$'}string',
          | int='${'$'}int',
          | bool='${'$'}bool')
          ""${'"'}.trimMargin()

          public fun patch(source: com.fasterxml.jackson.databind.node.ObjectNode): io.test.Test.Patch = io.test.Test.Patch(
            if (source.has("string")) Optional.ofNullable(string) else null,
            if (source.has("int")) Optional.ofNullable(int) else null,
            if (source.has("bool")) Optional.ofNullable(bool) else null
          )

          public data class Patch(
            public val string: java.util.Optional<kotlin.String>?,
            public val int: java.util.Optional<kotlin.Int>?,
            public val bool: java.util.Optional<kotlin.Boolean>?
          )
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

}
