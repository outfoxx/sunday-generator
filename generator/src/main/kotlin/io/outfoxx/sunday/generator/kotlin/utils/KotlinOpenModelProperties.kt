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
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import java.util.Collections

/** Enforces closed schemas and optionally preserves extensions using the emitted class hierarchy. */
internal fun addOpenModelProperties(
  modelTypes: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  options: Set<KotlinTypeRegistry.Option>,
  properties: GeneratedModelProperties,
  preserveUnknownFields: Boolean,
  typeName: (GeneratedTypeRef) -> TypeName,
) {
  if (KotlinTypeRegistry.Option.JacksonAnnotations !in options) {
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
    return builder
      .addOpenModelStorage(
        type,
        model.copy(patternProperties = properties.patternProperties(model)),
        namesByRoot.getValue(root(type)),
        parent,
        preserveUnknownFields,
        typeName,
        properties,
      ).also {
        completed[type] = it
      }
  }

  classes.keys.forEach(::decorate)
  classes.forEach { (type, entry) ->
    val (model, builder) = entry
    val storage = completed[type]?.takeIf { it.preserves } ?: return@forEach
    val guards = CodeBlock.builder()
    val wireNames = properties.fields(model).map { it.wireName }
    if (wireNames.isNotEmpty()) {
      val names =
        CodeBlock
          .builder()
          .apply {
            wireNames.forEachIndexed { index, name ->
              if (index > 0) add(", ")
              add("%S", name)
            }
          }.build()
      guards.addStatement("require(name !in setOf(%L)) { %S + name }", names, "Cannot replace a declared property: ")
    }
    if (properties.isClosed(model)) {
      val patterns = properties.patternProperties(model)
      val condition =
        CodeBlock
          .builder()
          .apply {
            if (patterns.isEmpty()) add("false")
            patterns.forEachIndexed { index, pattern ->
              if (index > 0) add(" || ")
              add("%T(%S).containsMatchIn(name)", Regex::class, pattern.pattern)
            }
          }.build()
      guards.addStatement("require(%L) { %S + name }", condition, "Additional properties are not allowed: ")
    }
    val guardCode = guards.build()
    if (guardCode.isEmpty()) return@forEach
    val setter = builder.funSpecs.firstOrNull { it.name == storage.setterName }
    if (setter != null) {
      builder.funSpecs[builder.funSpecs.indexOf(setter)] =
        setter
          .toBuilder()
          .clearBody()
          .addCode(guardCode)
          .addCode(setter.body)
          .build()
    } else {
      builder.addFunction(
        FunSpec
          .builder(storage.setterName)
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Preserves permitted fields without replacing declared or inherited model properties.\n")
          .addAnnotation(ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter"))
          .addParameter("name", STRING)
          .addParameter("value", storage.valueType)
          .addCode(guardCode)
          .addStatement("super.%N(name, value)", storage.setterName)
          .build(),
      )
    }
    if (KModifier.DATA in builder.build().modifiers) {
      val storageParameter =
        builder.build().primaryConstructor?.parameters?.firstOrNull { parameter ->
          parameter.annotations.any { it.typeName == ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter") }
        }
      if (storageParameter != null) {
        builder.addInitializerBlock(
          CodeBlock
            .builder()
            .beginControlFlow("for (name in %N.keys)", storageParameter.name)
            .add(guardCode)
            .endControlFlow()
            .build(),
        )
      }
    }
  }
}

/** Storage and setter contract shared by generated descendants. */
internal data class OpenModelExtensionStorage(
  val permitsName: String,
  val closed: Boolean,
  val setterName: String,
  val valueType: TypeName,
  val preserves: Boolean,
)

private fun TypeSpec.Builder.addOpenModelStorage(
  className: ClassName,
  model: GeneratedModel,
  knownNames: Set<String>,
  parent: OpenModelExtensionStorage?,
  preserve: Boolean,
  typeName: (GeneratedTypeRef) -> TypeName,
  properties: GeneratedModelProperties,
): OpenModelExtensionStorage? {
  val closed = model.closed == true || model.additionalProperties?.allowed == false || parent?.closed == true
  if (closed) {
    addAnnotation(AnnotationSpec.builder(JACKSON_JSON_IGNORE_PROPERTIES).addMember("ignoreUnknown = false").build())
  }
  if (model.patternProperties.isNotEmpty() ||
    (!closed && !preserve) ||
    (closed && parent?.closed == false && parent.permitsName.isEmpty())
  ) {
    return addPatternModelStorage(className, model, knownNames, parent, closed, preserve, typeName, properties)
  }
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
    val valueType = model.additionalProperties?.type?.let(typeName)
    if (!closed && valueType != null && valueType != parent.valueType) {
      addTypedExtensionDecoder(className, parent, valueType)
    }
    return parent.copy(closed = closed)
  }
  if (!closed && !preserve) return null

  val names = NameAllocator()
  knownNames.forEach { names.newName(it) }
  val inheritable = build().modifiers.any { it in setOf(KModifier.OPEN, KModifier.ABSTRACT, KModifier.SEALED) }
  if (closed) {
    val setterName = names.newName("rejectAdditionalProperty")
    addFunction(
      FunSpec
        .builder(setterName)
        .apply { if (inheritable) addModifiers(KModifier.OPEN) }
        .addKdoc("Rejects undeclared fields even when the mapper ignores unknown properties globally.\n")
        .addAnnotation(ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter"))
        .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNUSED_PARAMETER").build())
        .addParameter("name", STRING)
        .addParameter("value", ANY.copy(nullable = true))
        .addStatement("throw %T(%S + name)", IllegalArgumentException::class, "Additional properties are not allowed: ")
        .build(),
    )
    return OpenModelExtensionStorage("", closed = true, setterName, ANY.copy(nullable = true), preserves = false)
  }
  val storageName = names.newName("extensionFields")
  val accessorName = names.newName("additionalProperties")
  val setterName = names.newName("setAdditionalProperty")
  val permitsName = names.newName("permitsAdditionalProperties")
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
  // Schema refinements can change JVM representations (for example Double to Int).
  // Share storage, but let each model's Jackson decoder enforce its own value type.
  val storageType = if (inheritable) ANY.copy(nullable = true) else valueType
  val decoder =
    if (model.additionalProperties?.type != null) extensionDecoder(className, storageType, valueType) else null
  val mapType = MAP.parameterizedBy(STRING, storageType)
  val constructorStorage = addExtensionStorage(storageName, storageType)
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
      .addParameter("value", storageType)
      .apply {
        decoder?.let(::addAnnotation)
        if (inheritable) {
          addModifiers(KModifier.OPEN)
          addStatement("require(%N) { %S + name }", permitsName, "Additional properties are not allowed: ")
        }
      }.addStatement(
        if (constructorStorage) "%N = %N + (name to value)" else "%N[name] = value",
        *if (constructorStorage) arrayOf(storageName, storageName) else arrayOf(storageName),
      ).build(),
  )
  return OpenModelExtensionStorage(permitsName, closed = false, setterName, storageType, preserves = true)
}

/** Includes extension fields in data-class copies without sharing mutable map updates. */
internal fun TypeSpec.Builder.addExtensionStorage(
  name: String,
  valueType: TypeName,
): Boolean {
  val constructorStorage = KModifier.DATA in build().modifiers
  val mapType = (if (constructorStorage) MAP else MUTABLE_MAP).parameterizedBy(STRING, valueType)
  if (constructorStorage) {
    val constructor = requireNotNull(build().primaryConstructor).toBuilder()
    // The decoding-defaults pass consumes the annotation to exclude this implementation name from JSON binding.
    constructor.addParameter(
      ParameterSpec
        .builder(name, mapType)
        .defaultValue("emptyMap()")
        .addAnnotation(ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter"))
        .build(),
    )
    primaryConstructor(constructor.build())
  }
  addProperty(
    PropertySpec
      .builder(name, mapType, KModifier.PRIVATE)
      .mutable(constructorStorage)
      .initializer(
        if (constructorStorage) "%N" else "linkedMapOf()",
        *if (constructorStorage) arrayOf(name) else emptyArray(),
      ).build(),
  )
  // KotlinPoet only promotes properties preceding the initializer block into the primary constructor.
  if (constructorStorage && initializerIndex >= 0) initializerIndex = propertySpecs.size
  return constructorStorage
}

/** Keeps one inherited storage map while letting Jackson decode the child's complete extension type. */
private fun TypeSpec.Builder.addTypedExtensionDecoder(
  className: ClassName,
  parent: OpenModelExtensionStorage,
  valueType: TypeName,
) {
  val decoder = extensionDecoder(className, parent.valueType, valueType)
  addFunction(
    FunSpec
      .builder(parent.setterName)
      .addKdoc("Preserves extension values decoded according to this model's schema.\n")
      .addModifiers(KModifier.OVERRIDE)
      .addAnnotation(ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter"))
      .addAnnotation(decoder)
      .addParameter("name", STRING)
      .addParameter("value", parent.valueType)
      .addStatement("super.%N(name, value)", parent.setterName)
      .build(),
  )
}

private fun TypeSpec.Builder.extensionDecoder(
  className: ClassName,
  storageType: TypeName,
  valueType: TypeName,
): AnnotationSpec {
  val names = NameAllocator()
  build().typeSpecs.mapNotNull { it.name }.forEach { names.newName(it) }
  val decoderType = className.nestedClass(names.newName("AdditionalPropertyDeserializer"))
  addType(
    TypeSpec
      .classBuilder(decoderType)
      .addKdoc("Decodes inherited extension values with the active Jackson context and full generic type.\n")
      .superclass(JACKSON_JSON_DESERIALIZER.parameterizedBy(storageType))
      .addFunction(
        FunSpec
          .builder("deserialize")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("parser", JACKSON_JSON_PARSER)
          .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
          .returns(storageType)
          .addStatement(
            "return context.readValue(parser, context.typeFactory.constructType(object : %T() {}.type))",
            ClassName("com.fasterxml.jackson.core.type", "TypeReference").parameterizedBy(valueType),
          ).build(),
      ).addFunction(
        FunSpec
          .builder("getNullValue")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
          .returns(storageType)
          .apply {
            if (valueType.isNullable && storageType.isNullable) {
              addStatement("return null")
            } else {
              addStatement(
                "throw %T.from(context, %S)",
                ClassName("com.fasterxml.jackson.databind", "JsonMappingException"),
                "Null is not allowed for this model's additional properties",
              )
            }
          }.build(),
      ).build(),
  )
  return AnnotationSpec
    .builder(JACKSON_JSON_DESERIALIZE)
    .addMember("contentUsing = %T::class", decoderType)
    .build()
}
