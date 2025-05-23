/*
 * Copyright 2020 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asTypeName
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI
import java.time.Instant

@KotlinTest
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
    "kotlinType:client,             Client,   ~java.time.LocalDateTime",
  )
  fun `test type annotations`(
    annotationName: String,
    mode: GenerationMode,
    expectedPackageName: String,
  ) {

    val testAnnName = annotationName.split("""(?=[A-Z])|:""".toRegex()).joinToString("-") { it.lowercase() }
    val testRamlFile = "raml/type-gen/annotations/type-$testAnnName.raml"
    val testUri = resourceClassLoader.getResource(testRamlFile)?.toURI()
      ?: fail("unable to find test RAML file: $testRamlFile")

    val typeRegistry = KotlinTypeRegistry("io.test", null, mode, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry)
      .filterNot { it.key.simpleName == "API" }

    when (expectedPackageName[0]) {

      '+' ->
        assertEquals(
          expectedPackageName.substring(1),
          builtTypes.entries.first().key.packageName,
        )

      '~' ->
        assertEquals(
          expectedPackageName.substring(1),
          builtTypes.entries.first().value.propertySpecs.firstOrNull()?.type?.toString(),
        )
    }
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation`(
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Group", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.String

        public interface Group {
          public val `value`: String

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
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation (dashed scheme)`(
    @ResourceUri("raml/type-gen/annotations/type-nested-dashed.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Group", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.String

        public interface Group {
          public val `value`: String

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
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using library types`(
    @ResourceUri("raml/type-gen/annotations/type-nested-lib.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Root", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.String

        public interface Root {
          public val `value`: String

          public interface Group {
            public val `value`: String

            public interface Member {
              public val memberValue: String
            }
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using only library types`(
    @ResourceUri("raml/type-gen/annotations/type-nested-lib2.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

    val typeSpec = findType("io.test.Root", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.String

        public interface Root {
          public val `value`: String

          public interface Group {
            public val `value`: String

            public interface Member {
              public val memberValue: String
            }
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class generated kotlin implementations`(
    @ResourceUri("raml/type-gen/annotations/type-kotlin-impl.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel))

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

          public fun copy(): Test = Test()

          override fun toString(): String = ${'"'}""Test()""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated types`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

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
          JsonSubTypes.Type(value = Child1::class),
          JsonSubTypes.Type(value = Child2::class)
        ])
        public abstract class Parent {
          @get:JsonIgnore
          public abstract val type: String

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            return true
          }

          override fun toString(): String = ${'"'}""Parent()""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        @JsonTypeName("Child1")
        public class Child1(
          public val `value`: String? = null,
        ) : Parent() {
          override val type: String
            get() = "Child1"

          public fun copy(`value`: String? = null): Child1 = Child1(value ?: this.value)

          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child1

            if (value != other.value) return false

            return true
          }

          override fun toString(): String = ${'"'}""Child1(value='${'$'}value')""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        @JsonTypeName("child2")
        public class Child2(
          public val `value`: String? = null,
        ) : Parent() {
          override val type: String
            get() = "child2"

          public fun copy(`value`: String? = null): Child2 = Child2(value ?: this.value)

          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child2

            if (value != other.value) return false

            return true
          }

          override fun toString(): String = ${'"'}""Child2(value='${'$'}value')""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child2TypeSpec)
          .writeTo(this)
      },
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
            property = "parentType",
          )
          public val parent: Parent,
          public val parentType: String,
        ) {
          public fun copy(parent: Parent? = null, parentType: String? = null): Test = Test(parent ?:
              this.parent, parentType ?: this.parentType)

          override fun hashCode(): Int {
            var result = 1
            result = 31 * result + parent.hashCode()
            result = 31 * result + parentType.hashCode()
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

            if (parent != other.parent) return false
            if (parentType != other.parentType) return false

            return true
          }

          override fun toString(): String = ""${'"'}
          |Test(parent='${'$'}parent',
          | parentType='${'$'}parentType')
          ${'"'}"".trimMargin()
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated enum types`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-enum.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

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
          JsonSubTypes.Type(value = Child2::class),
          JsonSubTypes.Type(value = Child1::class)
        ])
        public abstract class Parent {
          @get:JsonIgnore
          public abstract val type: Type

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            return true
          }

          override fun toString(): String = ${'"'}""Parent()""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        @JsonTypeName("child-1")
        public class Child1(
          public val `value`: String? = null,
        ) : Parent() {
          override val type: Type
            get() = Type.Child1

          public fun copy(`value`: String? = null): Child1 = Child1(value ?: this.value)

          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child1

            if (value != other.value) return false

            return true
          }

          override fun toString(): String = ${'"'}""Child1(value='${'$'}value')""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        @JsonTypeName("child-2")
        public class Child2(
          public val `value`: String? = null,
        ) : Parent() {
          override val type: Type
            get() = Type.Child2

          public fun copy(`value`: String? = null): Child2 = Child2(value ?: this.value)

          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child2

            if (value != other.value) return false

            return true
          }

          override fun toString(): String = ${'"'}""Child2(value='${'$'}value')""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child2TypeSpec)
          .writeTo(this)
      },
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
            property = "parentType",
          )
          public val parent: Parent,
          public val parentType: Type,
        ) {
          public fun copy(parent: Parent? = null, parentType: Type? = null): Test = Test(parent ?:
              this.parent, parentType ?: this.parentType)

          override fun hashCode(): Int {
            var result = 1
            result = 31 * result + parent.hashCode()
            result = 31 * result + parentType.hashCode()
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

            if (parent != other.parent) return false
            if (parentType != other.parentType) return false

            return true
          }

          override fun toString(): String = ""${'"'}
          |Test(parent='${'$'}parent',
          | parentType='${'$'}parentType')
          ${'"'}"".trimMargin()
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated types with no discriminator property`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-no-property.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec = builtTypes[ClassName.bestGuess("io.test.Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonSubTypes

        @JsonSubTypes(value = [
          JsonSubTypes.Type(value = Child1::class),
          JsonSubTypes.Type(value = Child2::class)
        ])
        public open class Parent

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        @JsonTypeName("Child1")
        public class Child1(
          public val `value`: String? = null,
        ) : Parent() {
          public fun copy(`value`: String? = null): Child1 = Child1(value ?: this.value)

          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child1

            if (value != other.value) return false

            return true
          }

          override fun toString(): String = ${'"'}""Child1(value='${'$'}value')""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec = builtTypes[ClassName.bestGuess("io.test.Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        @JsonTypeName("child2")
        public class Child2(
          public val `value`: String? = null,
        ) : Parent() {
          public fun copy(`value`: String? = null): Child2 = Child2(value ?: this.value)

          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child2

            if (value != other.value) return false

            return true
          }

          override fun toString(): String = ${'"'}""Child2(value='${'$'}value')""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", child2TypeSpec)
          .writeTo(this)
      },
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
            property = "parentType",
          )
          public val parent: Parent,
          public val parentType: String,
        ) {
          public fun copy(parent: Parent? = null, parentType: String? = null): Test = Test(parent ?:
              this.parent, parentType ?: this.parentType)

          override fun hashCode(): Int {
            var result = 1
            result = 31 * result + parent.hashCode()
            result = 31 * result + parentType.hashCode()
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

            if (parent != other.parent) return false
            if (parentType != other.parentType) return false

            return true
          }

          override fun toString(): String = ""${'"'}
          |Test(parent='${'$'}parent',
          | parentType='${'$'}parentType')
          ${'"'}"".trimMargin()
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test external discriminator must exist`(
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry)
      }

    assertTrue(exception.message?.contains("External discriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel))

    val generatedTypes = generateTypes(testUri, typeRegistry)
    val typeSpec = findType("io.test.Test", generatedTypes)

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonInclude
        import io.outfoxx.sunday.json.patch.Patch
        import io.outfoxx.sunday.json.patch.PatchOp
        import io.outfoxx.sunday.json.patch.UpdateOp
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String
        import kotlin.Unit

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public open class Test(
          public var string: UpdateOp<String> = PatchOp.none(),
          public var int: UpdateOp<Int> = PatchOp.none(),
          public var bool: UpdateOp<Boolean> = PatchOp.none(),
          public var nullable: PatchOp<String> = PatchOp.none(),
          public var optional: UpdateOp<String> = PatchOp.none(),
          public var nullableOptional: PatchOp<String> = PatchOp.none(),
        ) : Patch {
          override fun hashCode(): Int {
            var result = 1
            result = 31 * result + string.hashCode()
            result = 31 * result + int.hashCode()
            result = 31 * result + bool.hashCode()
            result = 31 * result + nullable.hashCode()
            result = 31 * result + optional.hashCode()
            result = 31 * result + nullableOptional.hashCode()
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

            if (string != other.string) return false
            if (int != other.int) return false
            if (bool != other.bool) return false
            if (nullable != other.nullable) return false
            if (optional != other.optional) return false
            if (nullableOptional != other.nullableOptional) return false

            return true
          }

          override fun toString(): String = ""${'"'}
          |Test(string='${'$'}string',
          | int='${'$'}int',
          | bool='${'$'}bool',
          | nullable='${'$'}nullable',
          | optional='${'$'}optional',
          | nullableOptional='${'$'}nullableOptional')
          ""${'"'}.trimMargin()

          public companion object {
            public inline fun merge(`init`: Test.() -> Unit): PatchOp.Set<Test> {
              val patch = Test()
              patch.init()
              return PatchOp.Set(patch)
            }

            public inline fun patch(`init`: Test.() -> Unit): Test = merge(init).value
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Child", generatedTypes)

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonInclude
        import io.outfoxx.sunday.json.patch.Patch
        import io.outfoxx.sunday.json.patch.PatchOp
        import io.outfoxx.sunday.json.patch.UpdateOp
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String
        import kotlin.Unit

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public class Child(
          string: UpdateOp<String> = PatchOp.none(),
          int: UpdateOp<Int> = PatchOp.none(),
          bool: UpdateOp<Boolean> = PatchOp.none(),
          nullable: PatchOp<String> = PatchOp.none(),
          optional: UpdateOp<String> = PatchOp.none(),
          nullableOptional: PatchOp<String> = PatchOp.none(),
          public var child: UpdateOp<String> = PatchOp.none(),
        ) : Test(string, int, bool, nullable, optional, nullableOptional),
            Patch {
          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + child.hashCode()
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child

            if (string != other.string) return false
            if (int != other.int) return false
            if (bool != other.bool) return false
            if (nullable != other.nullable) return false
            if (optional != other.optional) return false
            if (nullableOptional != other.nullableOptional) return false
            if (child != other.child) return false

            return true
          }

          override fun toString(): String = ""${'"'}
          |Child(string='${'$'}string',
          | int='${'$'}int',
          | bool='${'$'}bool',
          | nullable='${'$'}nullable',
          | optional='${'$'}optional',
          | nullableOptional='${'$'}nullableOptional',
          | child='${'$'}child')
          ""${'"'}.trimMargin()

          public companion object {
            public inline fun merge(`init`: Child.() -> Unit): PatchOp.Set<Child> {
              val patch = Child()
              patch.init()
              return PatchOp.Set(patch)
            }

            public inline fun patch(`init`: Child.() -> Unit): Child = merge(init).value
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec2)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test discriminated patchable class generation`(
    @ResourceUri("raml/type-gen/annotations/type-patchable-disc.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ImplementModel, JacksonAnnotations))

    val generatedTypes = generateTypes(testUri, typeRegistry)
    val typeSpec = findType("io.test.Test", generatedTypes)

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonInclude
        import com.fasterxml.jackson.`annotation`.JsonSubTypes
        import com.fasterxml.jackson.`annotation`.JsonTypeInfo
        import io.outfoxx.sunday.json.patch.Patch
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.String

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonTypeInfo(
          use = JsonTypeInfo.Id.NAME,
          include = JsonTypeInfo.As.EXISTING_PROPERTY,
          property = "type",
        )
        @JsonSubTypes(value = [
          JsonSubTypes.Type(value = Child::class)
        ])
        public abstract class Test() : Patch {
          public abstract val type: TestType

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            return true
          }

          override fun toString(): String = ""${'"'}Test()""${'"'}
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Child", generatedTypes)

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonInclude
        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import io.outfoxx.sunday.json.patch.Patch
        import io.outfoxx.sunday.json.patch.PatchOp
        import io.outfoxx.sunday.json.patch.UpdateOp
        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String
        import kotlin.Unit

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonTypeName("child")
        public class Child(
          public var child: UpdateOp<String> = PatchOp.none(),
        ) : Test(),
            Patch {
          override val type: TestType
            get() = TestType.Child

          override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + child.hashCode()
            return result
          }

          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child

            if (child != other.child) return false

            return true
          }

          override fun toString(): String = ""${'"'}Child(child='${'$'}child')""${'"'}

          public companion object {
            public inline fun merge(`init`: Child.() -> Unit): PatchOp.Set<Child> {
              val patch = Child()
              patch.init()
              return PatchOp.Set(patch)
            }

            public inline fun patch(`init`: Child.() -> Unit): Child = merge(init).value
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec2)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test types can be generated in one mode and overridden in another`(
    @ResourceUri("raml/type-gen/annotations/type-kotlin-type-dual.raml") testUri: URI,
  ) {
    val valueTypeName = ClassName.bestGuess("io.test.Value")

    val serverTypeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())
    val serverTypes = generateTypes(testUri, serverTypeRegistry)

    val serverTypeSpec = findType("io.test.Test", serverTypes)

    val clientTypeRegistry = KotlinTypeRegistry("io.test", null, Client, setOf())
    val clientTypes = generateTypes(testUri, clientTypeRegistry)
    val clientTypeSpec = findType("io.test.Test", clientTypes)

    assertEquals(serverTypeSpec.propertySpecs.getOrNull(0)?.type, Instant::class.asTypeName())
    assertNull(serverTypes[valueTypeName])

    assertEquals(clientTypeSpec.propertySpecs.getOrNull(0)?.type, ClassName.bestGuess("io.test.Value"))
    assertNotNull(clientTypes[valueTypeName])
  }
}
