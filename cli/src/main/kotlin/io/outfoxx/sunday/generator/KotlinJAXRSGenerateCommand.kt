package io.outfoxx.sunday.generator

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry

class KotlinJAXRSGenerateCommand :
  KotlinGenerateCommand(name = "kotlin/jaxrs", help = "Generate Kotlin for JAX-RS framework") {

  override fun run() {
    val typeRegistry =
      KotlinTypeRegistry(
        modelPackageName ?: packageName,
        mode,
        options.toSet(),
      )

    files.forEach { file ->

      val generator =
        KotlinJAXRSGenerator(
          parseAndValidate(file.toURI()),
          typeRegistry,
          reactiveResponseType,
          servicePackageName ?: packageName,
          problemBaseUri,
          mediaTypes.toList()
        )

      generator.generateServiceTypes()
    }

    typeRegistry.buildTypes()
      .filter { it.key.topLevelClassName() == it.key }
      .map { FileSpec.get(it.key.packageName, it.value) }
      .map { it.writeTo(outputDirectory) }
  }

}
