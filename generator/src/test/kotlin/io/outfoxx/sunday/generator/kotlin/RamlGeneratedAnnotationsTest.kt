package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.AddGeneratedAnnotation
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.SuppressPublicApiWarnings
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Generated Annotations Test")
class RamlGeneratedAnnotationsTest {

  @Test
  fun `test generated annotation is added to root classes`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(AddGeneratedAnnotation))

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertTrue(type.toString().contains("@javax.`annotation`.processing.Generated"))
  }

  @Test
  fun `test warning annotations are added to hide public api`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(SuppressPublicApiWarnings))

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertTrue(type.toString().contains("@kotlin.Suppress"))
  }

  @Test
  fun `test generated annotation is added to service class`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(AddGeneratedAnnotation))

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    val type = findType("io.test.service.API", builtTypes)

    assertTrue(type.toString().contains("@javax.`annotation`.processing.Generated"))
  }

  @Test
  fun `test warning annotation is added to service class`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(SuppressPublicApiWarnings))

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    val type = findType("io.test.service.API", builtTypes)

    assertTrue(type.toString().contains("@kotlin.Suppress"))
  }

}
