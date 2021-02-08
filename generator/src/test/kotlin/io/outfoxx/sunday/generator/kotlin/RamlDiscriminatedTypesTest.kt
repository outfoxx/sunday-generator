package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
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
        @com.fasterxml.jackson.`annotation`.JsonTypeInfo(
          use = com.fasterxml.jackson.`annotation`.JsonTypeInfo.Id.NAME,
          include = com.fasterxml.jackson.`annotation`.JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @com.fasterxml.jackson.`annotation`.JsonSubTypes(value = [
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "Child1", value = io.test.Child1::class),
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "child2", value = io.test.Child2::class)
        ])
        public interface Parent {
          public val type: kotlin.String
        }
        
      """.trimIndent(),
      parenTypeSpec.toString()
    )

    val child1TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        public interface Child1 : io.test.Parent {
          public val value: kotlin.String?
        }
        
      """.trimIndent(),
      child1TypeSpec.toString()
    )

    val child2TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        public interface Child2 : io.test.Parent {
          public val value: kotlin.String?
        }
        
      """.trimIndent(),
      child2TypeSpec.toString()
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
        @com.fasterxml.jackson.`annotation`.JsonTypeInfo(
          use = com.fasterxml.jackson.`annotation`.JsonTypeInfo.Id.NAME,
          include = com.fasterxml.jackson.`annotation`.JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @com.fasterxml.jackson.`annotation`.JsonSubTypes(value = [
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "Child1", value = io.test.Child1::class),
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "child2", value = io.test.Child2::class)
        ])
        public abstract class Parent {
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
        @com.fasterxml.jackson.`annotation`.JsonTypeInfo(
          use = com.fasterxml.jackson.`annotation`.JsonTypeInfo.Id.NAME,
          include = com.fasterxml.jackson.`annotation`.JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @com.fasterxml.jackson.`annotation`.JsonSubTypes(value = [
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "Child1", value = io.test.Child1::class),
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "Child2", value = io.test.Child2::class)
        ])
        public interface Parent {
          public val type: io.test.Type
        }
        
      """.trimIndent(),
      parenTypeSpec.toString()
    )

    val child1TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        public interface Child1 : io.test.Parent {
          public val value: kotlin.String?
        }
        
      """.trimIndent(),
      child1TypeSpec.toString()
    )

    val child2TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        public interface Child2 : io.test.Parent {
          public val value: kotlin.String?
        }
        
      """.trimIndent(),
      child2TypeSpec.toString()
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
        @com.fasterxml.jackson.`annotation`.JsonTypeInfo(
          use = com.fasterxml.jackson.`annotation`.JsonTypeInfo.Id.NAME,
          include = com.fasterxml.jackson.`annotation`.JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type"
        )
        @com.fasterxml.jackson.`annotation`.JsonSubTypes(value = [
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "Child1", value = io.test.Child1::class),
          com.fasterxml.jackson.`annotation`.JsonSubTypes.Type(name = "Child2", value = io.test.Child2::class)
        ])
        public abstract class Parent {
          public abstract val type: io.test.Type
        
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
          public override val type: io.test.Type
            get() = io.test.Type.Child1

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
          public override val type: io.test.Type
            get() = io.test.Type.Child2

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

  }

}
