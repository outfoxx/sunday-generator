/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry

/** Registers inherited declared fields in the parameter storage used by Quarkiverse serializers and copies. */
internal fun addQuarkusProblemExtensions(
  models: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  properties: GeneratedModelProperties,
  options: Set<KotlinTypeRegistry.Option>,
) {
  fun root(name: ClassName): ClassName? =
    when (val parent = models[name]?.second?.build()?.superclass) {
      QUARKUS_HTTP_PROBLEM -> name
      is ClassName -> root(parent)
      else -> null
    }

  models.keys.groupBy(::root).forEach { (root, hierarchy) ->
    if (root == null) return@forEach
    val runtimeWriter =
      models
        .getValue(root)
        .second
        .build()
        .tag(ProblemExtensionWriter::class)
        ?.name
    if (hierarchy.size == 1 && runtimeWriter == null) return@forEach
    val builder = models.getValue(root).second
    val type = builder.build()
    val constructor = type.primaryConstructor ?: return@forEach
    val names = NameAllocator()
    hierarchy
      .flatMap { name ->
        val declaration = models.getValue(name).second.build()
        declaration.propertySpecs.map { it.name } + declaration.funSpecs.map { it.name } +
          declaration.primaryConstructor
            ?.parameters
            .orEmpty()
            .map { it.name }
      }.distinct()
      .forEach { names.newName(it) }
    val builderName = names.newName("problemBuilder")
    val registerName = runtimeWriter ?: names.newName("registerProblemExtension")
    val builderType = QUARKUS_HTTP_PROBLEM.nestedClass("Builder")

    // HttpProblem keeps an unmodifiable view of its builder's map, including in builder(original).
    // Retain that builder without exposing it as a public constructor argument or reading child getters
    // during base construction. Each descendant registers only after its own fields are initialized.
    builder.primaryConstructor(
      constructor
        .toBuilder()
        .apply {
          modifiers.clear()
          addModifiers(KModifier.PRIVATE)
          annotations.clear()
          parameters.add(0, ParameterSpec.builder(builderName, builderType).build())
        }.build(),
    )
    builder.addFunction(
      constructor
        .toBuilder()
        .apply {
          modifiers.clear()
          addModifiers(if (KModifier.SEALED in type.modifiers) KModifier.PROTECTED else KModifier.PUBLIC)
          addKdoc("Creates a problem with its declared fields.\n")
          parameters.replaceAll { parameter ->
            parameter
              .toBuilder()
              .apply {
                annotations.replaceAll { it.toBuilder().useSiteTarget(null).build() }
              }.build()
          }
          if (KotlinTypeRegistry.Option.JacksonAnnotations in options &&
            type.typeSpecs.flatMap { it.funSpecs }.none { function ->
              function.annotations.any { it.typeName == JACKSON_JSON_CREATOR }
            }
          ) {
            addAnnotation(JACKSON_JSON_CREATOR)
          }
          callThisConstructor(
            listOf(type.superclassConstructorParameters.single()) +
              constructor.parameters.map { CodeBlock.of("%N", it.name) },
          )
        }.build(),
    )
    builder.superclassConstructorParameters.clear()
    builder.addSuperclassConstructorParameter("%N", builderName)
    builder.addProperty(
      PropertySpec.builder(builderName, builderType, KModifier.PRIVATE).initializer("%N", builderName).build(),
    )
    builder.addFunction(
      FunSpec
        .builder(registerName)
        .addModifiers(KModifier.PROTECTED)
        .addKdoc("Adds a validated dynamic or initialized declared extension to the runtime problem representation.\n")
        .addParameter("name", STRING)
        .addParameter("value", ANY.copy(nullable = true))
        .addStatement("%N.with(name, value)", builderName)
        .build(),
    )
    builder.initializerIndex = builder.propertySpecs.size

    hierarchy.filterNot { it == root }.forEach { name ->
      val (model, descendant) = models.getValue(name)
      val fields = properties.fields(model).associateBy { it.storage.name.kotlinIdentifierName }
      val registration = CodeBlock.builder()
      descendant.propertySpecs.forEach propertyLoop@{ property ->
        val field = fields[property.name] ?: return@propertyLoop
        if (field.wireName in setOf("type", "title", "status", "detail", "instance")) return@propertyLoop
        val omitNull = !field.effective.required && !properties.acceptsNull(field.effective.type)
        if (omitNull) registration.beginControlFlow("if (this.%N != null)", property.name)
        registration.addStatement("%N(%S, this.%N)", registerName, field.wireName, property.name)
        if (omitNull) registration.endControlFlow()
      }
      if (!registration.build().isEmpty()) {
        descendant.addInitializerBlock(registration.build())
        descendant.initializerIndex = descendant.propertySpecs.size
      }
    }
  }
}
