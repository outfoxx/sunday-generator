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
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import java.util.Collections

/** Adds extension storage using the emitted class hierarchy rather than schema inheritance. */
internal fun addOpenModelProperties(
  modelTypes: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  options: Set<KotlinTypeRegistry.Option>,
  typeName: (GeneratedTypeRef) -> TypeName,
) {
  if (KotlinTypeRegistry.Option.PreserveUnknownFields !in options ||
    KotlinTypeRegistry.Option.JacksonAnnotations !in options
  ) {
    return
  }
  val classes =
    modelTypes.filterValues { (model, type) ->
      model.kind == GeneratedModel.Kind.OBJECT && type.build().kind == TypeSpec.Kind.CLASS
    }
  val parents = classes.mapValues { (_, value) -> value.second.build().superclass as? ClassName }

  fun root(type: ClassName): ClassName {
    var current = type
    val visited = mutableSetOf<ClassName>()
    while (visited.add(current)) {
      current = parents[current]?.takeIf { it in classes } ?: return current
    }
    error("Cyclic generated model hierarchy: $type")
  }

  // A base accessor must not collide with a declared field on any descendant.
  val namesByRoot =
    classes.entries.groupBy { root(it.key) }.mapValues { (_, entries) ->
      entries
        .flatMap {
          it.value.second
            .build()
            .propertySpecs
        }.mapTo(mutableSetOf()) { it.name }
    }
  val completed = mutableMapOf<ClassName, OpenModelExtensionStorage?>()

  fun decorate(type: ClassName): OpenModelExtensionStorage? {
    if (type in completed) return completed[type]
    val (model, builder) = classes.getValue(type)
    val parent = parents[type]?.takeIf { it in classes }?.let(::decorate)
    return builder.addOpenModelStorage(model, namesByRoot.getValue(root(type)), parent, typeName).also {
      completed[type] = it
    }
  }

  classes.keys.forEach(::decorate)
}

private data class OpenModelExtensionStorage(
  val permitsName: String,
  val closed: Boolean,
)

private fun TypeSpec.Builder.addOpenModelStorage(
  model: GeneratedModel,
  knownNames: Set<String>,
  parent: OpenModelExtensionStorage?,
  typeName: (GeneratedTypeRef) -> TypeName,
): OpenModelExtensionStorage? {
  val closed = model.closed == true || model.additionalProperties?.allowed == false || parent?.closed == true
  if (parent != null) {
    if (closed && !parent.closed) {
      addProperty(
        PropertySpec
          .builder(
            parent.permitsName,
            BOOLEAN,
            KModifier.PROTECTED,
            KModifier.OVERRIDE,
          ).addKdoc("Keeps this closed schema from accepting its parent's extension fields.\n")
          .initializer("false")
          .build(),
      )
    }
    return parent.copy(closed = closed)
  }
  if (closed) return null

  val names = NameAllocator()
  knownNames.forEach { names.newName(it) }
  val storageName = names.newName("extensionFields")
  val accessorName = names.newName("additionalProperties")
  val setterName = names.newName("setAdditionalProperty")
  val permitsName = names.newName("permitsAdditionalProperties")
  val inheritable = build().modifiers.any { it in setOf(KModifier.OPEN, KModifier.ABSTRACT, KModifier.SEALED) }
  if (inheritable) {
    addProperty(
      PropertySpec
        .builder(permitsName, BOOLEAN, KModifier.PROTECTED, KModifier.OPEN)
        .addKdoc("Allows closed descendants to reject fields handled by the inherited extension setter.\n")
        .initializer("true")
        .build(),
    )
  }
  val valueType = model.additionalProperties?.type?.let(typeName) ?: ANY.copy(nullable = true)
  val mapType = MAP.parameterizedBy(STRING, valueType)
  addProperty(
    PropertySpec
      .builder(storageName, MUTABLE_MAP.parameterizedBy(STRING, valueType), KModifier.PRIVATE)
      .initializer("linkedMapOf()")
      .build(),
  )
  addProperty(
    PropertySpec
      .builder(accessorName, mapType, KModifier.PUBLIC)
      .addKdoc("Permitted fields not declared by this version of the contract, at their original JSON level.\n")
      .addAnnotation(
        AnnotationSpec
          .builder(ClassName("com.fasterxml.jackson.annotation", "JsonAnyGetter"))
          .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
          .build(),
      ).addAnnotation(
        AnnotationSpec
          .builder(JACKSON_JSON_INCLUDE)
          .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
          .addMember(
            "value = %T.ALWAYS, content = %T.ALWAYS",
            JACKSON_JSON_INCLUDE_INCLUDE,
            JACKSON_JSON_INCLUDE_INCLUDE,
          ).build(),
      ).getter(
        FunSpec.getterBuilder().addStatement("return %T.unmodifiableMap(%N)", Collections::class, storageName).build(),
      ).build(),
  )
  addFunction(
    FunSpec
      .builder(setterName)
      .addKdoc("Preserves one extension field, including a permitted explicit null.\n")
      .addAnnotation(ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter"))
      .addParameter("name", STRING)
      .addParameter("value", valueType)
      .apply {
        if (inheritable) {
          addStatement("require(%N) { %S + name }", permitsName, "Additional properties are not allowed: ")
        }
      }.addStatement("%N[name] = value", storageName)
      .build(),
  )
  return OpenModelExtensionStorage(permitsName, closed = false)
}
