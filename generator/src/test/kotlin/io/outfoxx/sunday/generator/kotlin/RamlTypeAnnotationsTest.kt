package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Type Annotations Test")
class RamlTypeAnnotationsTest {

  companion object {

    private val resourceClassLoader = Thread.currentThread().contextClassLoader
  }

  @ParameterizedTest(name = "{0} in {1} mode")
  @CsvSource(
    "kotlinModelPackage,            Server,   +io.explicit.test",
    "kotlinModelPackage,            Client,   +io.explicit.test",
    "kotlinModelPackage:server,     Server,   +io.explicit.test.server",
    "kotlinModelPackage:server,     Client,   +io.explicit.test",
    "kotlinModelPackage:client,     Server,   +io.explicit.test",
    "kotlinModelPackage:client,     Client,   +io.explicit.test.client",
    "kotlinPackage,                 Server,   +io.explicit.test",
    "kotlinPackage,                 Client,   +io.explicit.test",
    "kotlinPackage:server,          Server,   +io.explicit.test.server",
    "kotlinPackage:server,          Client,   +io.explicit.test",
    "kotlinPackage:client,          Server,   +io.explicit.test",
    "kotlinPackage:client,          Client,   +io.explicit.test.client",
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
        package io.test

        import kotlin.String

        public interface Group {
          public val value: String

          public interface Member1 : Group {
            public val memberValue1: String

            public interface Sub : Member1 {
              public val subMemberValue: String
            }
          }
        
          public interface Member2 : Group {
            public val memberValue2: String
          }
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
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
        package io.test

        import kotlin.String

        public interface Root {
          public val value: String

          public interface Group {
            public val value: String
  
            public interface Member {
              public val memberValue: String
            }
          }
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
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
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonIgnore
        import java.time.LocalDateTime
        import kotlin.String

        public class Test {
          @get:JsonIgnore
          public val className: String
            get() = LocalDateTime::class.qualifiedName + "-value-" + "-literal"

          public fun copy() = Test()
        
          public override fun toString() = ${'"'}""Test()""${'"'}
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
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
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonIgnore
        import com.fasterxml.jackson.`annotation`.JsonSubTypes
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.String

        @JsonSubTypes(value = [
          JsonSubTypes.Type(name = "Child1", value = Child1::class),
          JsonSubTypes.Type(name = "child2", value = Child2::class)
        ])
        public abstract class Parent {
          @get:JsonIgnore
          public abstract val type: String
        
          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            return true
          }

          public override fun toString() = ${'"'}""Parent()""${'"'}
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", parenTypeSpec)
          .writeTo(this)
      }
    )

    val child1TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        public class Child1(
          public val value: String?
        ) : Parent() {
          public override val type: String
            get() = "Child1"

          public fun copy(value: String? = null) = Child1(value ?: this.value)

          public override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child1

            if (!super.equals(other)) return false
            if (value != other.value) return false

            return true
          }

          public override fun toString() = ${'"'}""Child1(value='${'$'}value')""${'"'}
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child1TypeSpec)
          .writeTo(this)
      }
    )

    val child2TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        public class Child2(
          public val value: String?
        ) : Parent() {
          public override val type: String
            get() = "child2"

          public fun copy(value: String? = null) = Child2(value ?: this.value)
        
          public override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }
        
          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as Child2
        
            if (!super.equals(other)) return false
            if (value != other.value) return false
        
            return true
          }
        
          public override fun toString() = ${'"'}""Child2(value='${'$'}value')""${'"'}
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child2TypeSpec)
          .writeTo(this)
      }
    )

    val testTypeSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
      ?: error("Test type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonTypeInfo
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        public class Test(
          @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "parentType"
          )
          public val parent: Parent,
          public val parentType: String
        ) {
          public fun copy(parent: Parent? = null, parentType: String? = null) = Test(parent ?: this.parent,
              parentType ?: this.parentType)
        
          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + parent.hashCode()
            result = 31 * result + parentType.hashCode()
            return result
          }
        
          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as Test
        
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
      buildString {
        FileSpec.get("io.test", testTypeSpec)
          .writeTo(this)
      }
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
        package io.test

        import com.fasterxml.jackson.databind.node.ObjectNode
        import java.util.Optional
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        public class Test(
          public val string: String,
          public val int: Int,
          public val bool: Boolean
        ) {
          public fun copy(
            string: String? = null,
            int: Int? = null,
            bool: Boolean? = null
          ) = Test(string ?: this.string, int ?: this.int, bool ?: this.bool)

          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + string.hashCode()
            result = 31 * result + int.hashCode()
            result = 31 * result + bool.hashCode()
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

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

          public fun patch(source: ObjectNode): Patch = Patch(
            if (source.has("string")) Optional.ofNullable(string) else null,
            if (source.has("int")) Optional.ofNullable(int) else null,
            if (source.has("bool")) Optional.ofNullable(bool) else null
          )

          public data class Patch(
            public val string: Optional<String>?,
            public val int: Optional<Int>?,
            public val bool: Optional<Boolean>?
          )
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )

  }

}
