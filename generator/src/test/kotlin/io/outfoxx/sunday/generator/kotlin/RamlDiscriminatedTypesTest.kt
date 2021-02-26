package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Discriminated Types Test")
class RamlDiscriminatedTypesTest {

  @Test
  fun `test polymorphism added to generated interfaces of string discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec = builtTypes[ClassName.bestGuess("io.test.Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonSubTypes
        import com.fasterxml.jackson.`annotation`.JsonTypeInfo
        import kotlin.String

        @JsonTypeInfo(
          use = JsonTypeInfo.Id.NAME,
          include = JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @JsonSubTypes(value = [
          JsonSubTypes.Type(name = "Child1", value = Child1::class),
          JsonSubTypes.Type(name = "child2", value = Child2::class)
        ])
        public interface Parent {
          public val type: String
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

        import kotlin.Int
        import kotlin.String

        public interface Child1 : Parent {
          public val value: String?

          public val value1: Int
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

        import kotlin.Int
        import kotlin.String

        public interface Child2 : Parent {
          public val value: String?

          public val value2: Int
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child2TypeSpec)
          .writeTo(this)
      }
    )

  }

  @Test
  fun `test polymorphism added to generated classes of string discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec = builtTypes[ClassName.bestGuess("io.test.Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonSubTypes
        import com.fasterxml.jackson.`annotation`.JsonTypeInfo
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.String

        @JsonTypeInfo(
          use = JsonTypeInfo.Id.NAME,
          include = JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @JsonSubTypes(value = [
          JsonSubTypes.Type(name = "Child1", value = Child1::class),
          JsonSubTypes.Type(name = "child2", value = Child2::class)
        ])
        public abstract class Parent {
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
          public val value: String?,
          public val value1: Int
        ) : Parent() {
          public override val type: String
            get() = "Child1"

          public fun copy(value: String? = null, value1: Int? = null) = Child1(value ?: this.value, value1
              ?: this.value1)

          public override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            result = 31 * result + value1.hashCode()
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child1

            if (!super.equals(other)) return false
            if (value != other.value) return false
            if (value1 != other.value1) return false

            return true
          }

          public override fun toString() = ""${'"'}
          |Child1(value='${'$'}value',
          | value1='${'$'}value1')
          ""${'"'}.trimMargin()
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
          public val value: String?,
          public val value2: Int
        ) : Parent() {
          public override val type: String
            get() = "child2"

          public fun copy(value: String? = null, value2: Int? = null) = Child2(value ?: this.value, value2
              ?: this.value2)
        
          public override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            result = 31 * result + value2.hashCode()
            return result
          }
        
          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as Child2
        
            if (!super.equals(other)) return false
            if (value != other.value) return false
            if (value2 != other.value2) return false
        
            return true
          }
        
          public override fun toString() = ""${'"'}
          |Child2(value='${'$'}value',
          | value2='${'$'}value2')
          ""${'"'}.trimMargin()
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child2TypeSpec)
          .writeTo(this)
      }
    )

  }

  @Test
  fun `test polymorphism added to generated interfaces of enum discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec = builtTypes[ClassName.bestGuess("io.test.Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonSubTypes
        import com.fasterxml.jackson.`annotation`.JsonTypeInfo

        @JsonTypeInfo(
          use = JsonTypeInfo.Id.NAME,
          include = JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @JsonSubTypes(value = [
          JsonSubTypes.Type(name = "Child1", value = Child1::class),
          JsonSubTypes.Type(name = "Child2", value = Child2::class)
        ])
        public interface Parent {
          public val type: Type
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

        import kotlin.String

        public interface Child1 : Parent {
          public val value: String?
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

        import kotlin.String

        public interface Child2 : Parent {
          public val value: String?
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child2TypeSpec)
          .writeTo(this)
      }
    )

  }

  @Test
  fun `test polymorphism added to generated classes of enum discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec = builtTypes[ClassName.bestGuess("io.test.Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonSubTypes
        import com.fasterxml.jackson.`annotation`.JsonTypeInfo
        import kotlin.Any
        import kotlin.Boolean

        @JsonTypeInfo(
          use = JsonTypeInfo.Id.NAME,
          include = JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @JsonSubTypes(value = [
          JsonSubTypes.Type(name = "Child1", value = Child1::class),
          JsonSubTypes.Type(name = "Child2", value = Child2::class)
        ])
        public abstract class Parent {
          public abstract val type: Type
        
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
          public override val type: Type
            get() = Type.Child1

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
          public override val type: Type
            get() = Type.Child2

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

  }

}
