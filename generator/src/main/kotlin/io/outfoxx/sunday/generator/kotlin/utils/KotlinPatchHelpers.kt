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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Adds conversion adapters and a package-shared RFC 7396 implementation. */
internal fun addKotlinPatchHelpers(
  models: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  validation: BeanValidationTypes?,
  declarations: List<GeneratedModel>,
  allModels: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>> = models,
  methodNames: Pair<String, String>? = null,
  reservedTypes: List<ClassName> = emptyList(),
  registerSupport: (ClassName, TypeSpec.Builder, List<GeneratedModel>) -> Unit,
) {
  val index =
    io.outfoxx.sunday.generator.ir.emit
      .GeneratedModelIndex(allModels.values.map { it.first })
  val pairs =
    models.entries.mapNotNull { patch ->
      val declaration =
        patch.value.first.patchOf
          ?.let(index::modelOrNull)
      val original = models.entries.singleOrNull { it.value.first == declaration }
      original?.let { it to patch }
    }
  if (pairs.isEmpty()) return
  val names = NameAllocator()
  allModels.values
    .flatMap { (_, builder) -> builder.propertySpecs.map { it.name } + builder.funSpecs.map { it.name } }
    .distinct()
    .forEach { names.newName(it) }
  val patchMethod = methodNames?.first ?: names.newName("patch")
  val mergeMethod = methodNames?.second ?: names.newName("merge")
  val groups = pairs.groupBy { it.first.key.packageName }
  if (groups.size > 1) {
    groups.values.forEach { group ->
      addKotlinPatchHelpers(
        group.flatMap { (original, patch) -> listOf(original.toPair(), patch.toPair()) }.toMap(),
        validation,
        declarations,
        allModels,
        patchMethod to mergeMethod,
        reservedTypes,
        registerSupport,
      )
    }
    return
  }
  val packageName = groups.keys.single()
  val supportNames = NameAllocator()
  (allModels.keys + reservedTypes)
    .filter { it.packageName == packageName }
    .map { it.topLevelClassName().simpleName }
    .distinct()
    .forEach { supportNames.newName(it) }
  val support = ClassName(packageName, supportNames.newName("MergePatchSupport"))
  val mapper = ClassName("com.fasterxml.jackson.databind", "ObjectMapper")
  val node = ClassName("com.fasterxml.jackson.databind", "JsonNode")
  val mapperParameter = ParameterSpec.builder("mapper", mapper).defaultValue("%T.mapper", support).build()
  pairs.forEach { (original, patch) ->
    val builder = original.value.second
    val tag =
      patch.value.first.properties
        .firstOrNull { it.patchDiscriminator }
        ?.let { it.serializationName ?: it.name }
    val tagCode = tag?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null")
    val parentHasPair =
      original.value.first.inherits.any { parent ->
        allModels.values.any { it.first.patchOf?.let(index::modelOrNull) == index.modelOrNull(parent) }
      }
    val snapshot =
      FunSpec
        .builder(patchMethod)
        .addKdoc("Creates a merge-patch snapshot; a required null value cannot be represented.\n")
        .returns(patch.key)
        .addParameter(if (parentHasPair) ParameterSpec.builder("mapper", mapper).build() else mapperParameter)
        .apply {
          if (parentHasPair) {
            addModifiers(KModifier.OVERRIDE)
          } else if (KModifier.OPEN in builder.modifiers || KModifier.SEALED in builder.modifiers) {
            addModifiers(KModifier.OPEN)
          }
          addStatement("return %T.patch(this, null, %T::class.java, mapper, %L)", support, patch.key, tagCode)
        }.build()
    builder.addFunction(snapshot)
    builder.addFunction(
      FunSpec
        .builder(patchMethod)
        .addKdoc("Creates only the changes from [from]; unchanged nulls are preserved.\n")
        .addParameter("from", original.key)
        .addParameter(mapperParameter)
        .returns(patch.key)
        .addStatement("return %T.patch(this, from, %T::class.java, mapper, %L)", support, patch.key, tagCode)
        .build(),
    )
    // Keep model class literals on the adapter so a shared helper never references excluded declarations.
    val defaults =
      CodeBlock
        .builder()
        .add("setOf<Class<*>>(")
        .apply {
          reachablePatchModels(listOf(original.value.first, patch.value.first), allModels, declarations)
            .filterValues { (_, b) ->
              b.typeSpecs.any {
                it.isCompanion &&
                  it.funSpecs.any { f -> f.annotations.any { a -> a.typeName == JACKSON_JSON_CREATOR } }
              }
            }.keys
            .forEach { add("%T::class.java,", it) }
        }.add(")")
        .build()
    builder.addFunction(
      FunSpec
        .builder(mergeMethod)
        .addKdoc("Recursively merges [patch] into a new validated model without changing this instance.\n")
        .addParameter("patch", patch.key)
        .addParameter(mapperParameter)
        .returns(original.key)
        .addStatement(
          "return %T.apply(this, patch, %T::class.java, mapper, %L, %L)",
          support,
          original.key,
          tagCode,
          defaults,
        ).build(),
    )
    val companion =
      patch.value.second.typeSpecs
        .firstOrNull { it.isCompanion }
    val factoryNames = NameAllocator()
    companion?.funSpecs?.forEach { factoryNames.newName(it.name) }
    val factory =
      FunSpec
        .builder(factoryNames.newName("fromModel"))
        .addKdoc("Creates a patch snapshot by delegating to the ordinary model's conversion.\n")
        .addParameter("model", original.key)
        .addParameter(mapperParameter)
        .returns(patch.key)
        .addStatement("return model.%N(mapper)", patchMethod)
        .build()
    val updated = (companion?.toBuilder() ?: TypeSpec.companionObjectBuilder()).addFunction(factory).build()
    if (companion == null) {
      patch.value.second.addType(updated)
    } else {
      patch.value.second.typeSpecs[
        patch.value.second.typeSpecs
          .indexOf(companion),
      ] = updated
    }
  }
  val helper =
    TypeSpec
      .objectBuilder(support)
      .addModifiers(KModifier.INTERNAL)
      .addKdoc("Shared wire-tree operations for generated merge-patch companions.\n")
      .addProperty(PropertySpec.builder("mapper", mapper).initializer("%T().findAndRegisterModules()", mapper).build())
  val generic = TypeVariableName("T", Any::class)
  helper.addFunction(
    FunSpec
      .builder("patch")
      .addTypeVariable(generic)
      .addParameter("value", Any::class)
      .addParameter(
        "original",
        ANY
          .copy(nullable = true),
      ).addParameter(
        "type",
        ClassName("java.lang", "Class").let {
          it.parameterizedBy(generic)
        },
      ).addParameter("mapper", mapper)
      .addParameter("discriminator", String::class.asTypeName().copy(nullable = true))
      .returns(generic)
      .apply {
        validation?.let { addStatement("%T.response(value)", it.modelValidation) }
        addStatement(
          "val tree = difference(mapper.valueToTree(value), original?.let { mapper.valueToTree<%T>(it) })",
          node,
        )
        beginControlFlow("if (discriminator != null)")
        addStatement("val current = mapper.valueToTree<%T>(value).get(discriminator)", node)
        addStatement(
          "require(original == null || mapper.valueToTree<%T>(original).get(discriminator) == current) { %S }",
          node,
          "A merge patch cannot change the discriminator",
        )
        addStatement(
          "(tree as %T).set<%T>(discriminator, current)",
          ClassName("com.fasterxml.jackson.databind.node", "ObjectNode"),
          node,
        )
        endControlFlow()
        addStatement("val result = mapper.treeToValue(tree, type)")
        addStatement(
          "require(mapper.valueToTree<%T>(result) == tree) { %S }",
          node,
          "Patch conversion changed the wire value",
        )
        addStatement("return result")
      }.build(),
  )
  helper.addFunction(
    FunSpec
      .builder("apply")
      .addTypeVariable(generic)
      .addParameter("value", generic)
      .addParameter("patch", Any::class)
      .addParameter(
        "type",
        ClassName("java.lang", "Class").let {
          it.parameterizedBy(generic)
        },
      ).addParameter("mapper", mapper)
      .addParameter("discriminator", String::class.asTypeName().copy(nullable = true))
      .addParameter(
        "defaults",
        ClassName("kotlin.collections", "Set").parameterizedBy(ClassName("java.lang", "Class").parameterizedBy(STAR)),
      ).returns(generic)
      .apply {
        validation?.let {
          addStatement("%T.response(value)", it.modelValidation)
          addStatement("%T.response(patch)", it.modelValidation)
        }
        beginControlFlow("if (discriminator != null)")
        addStatement(
          "require(mapper.valueToTree<%T>(value).get(discriminator) == mapper.valueToTree<%T>(patch).get(discriminator)) { %S }",
          node,
          node,
          "A merge patch cannot change the discriminator",
        )
        endControlFlow()
        addStatement("val tree = merge(mapper.valueToTree(value), mapper.valueToTree(patch))")
        addStatement("val reader = mapper.copy()")
        addStatement(
          "reader.setAnnotationIntrospector(%T.pair(Defaults(defaults), mapper.deserializationConfig.annotationIntrospector))",
          ClassName("com.fasterxml.jackson.databind", "AnnotationIntrospector"),
        )
        addStatement("val result = reader.treeToValue(tree, type)")
        validation?.let { addStatement("%T.response(result)", it.modelValidation) }
        addStatement("return result")
      }.build(),
  )
  helper.addFunction(
    FunSpec
      .builder("difference")
      .addModifiers(KModifier.PRIVATE)
      .addParameter("value", node)
      .addParameter("original", node.copy(nullable = true))
      .returns(node)
      .addCode(
        """
        if (!value.isObject) return value.deepCopy()
        val result = mapper.createObjectNode()
        value.properties().forEach { (key, item) ->
          if (original == null || original.get(key) != item) {
            result.set<%T>(key, difference(item, original?.get(key)))
          }
        }
        original?.fieldNames()?.forEachRemaining { key ->
          if (!value.has(key)) result.putNull(key)
        }
        return result
        """.trimIndent() + "\n",
        node,
      ).build(),
  )
  helper.addFunction(
    FunSpec
      .builder("merge")
      .addModifiers(KModifier.PRIVATE)
      .addParameter("value", node.copy(nullable = true))
      .addParameter("patch", node)
      .returns(node)
      .addCode(
        """
        if (!patch.isObject) return patch.deepCopy()
        val result = (value as? %T)?.deepCopy() ?: mapper.createObjectNode()
        patch.properties().forEach { (key, item) ->
          if (item.isNull) result.remove(key)
          else result.set<%T>(key, merge(result.get(key), item))
        }
        return result
        """.trimIndent() + "\n",
        ClassName("com.fasterxml.jackson.databind.node", "ObjectNode"),
        node,
      ).build(),
  )
  helper.addType(
    TypeSpec
      .classBuilder("Defaults")
      .primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addParameter(
            "types",
            ClassName(
              "kotlin.collections",
              "Set",
            ).parameterizedBy(ClassName("java.lang", "Class").parameterizedBy(STAR)),
          ).build(),
      ).addProperty(
        PropertySpec
          .builder(
            "types",
            ClassName(
              "kotlin.collections",
              "Set",
            ).parameterizedBy(ClassName("java.lang", "Class").parameterizedBy(STAR)),
          ).initializer("types")
          .addModifiers(KModifier.PRIVATE)
          .build(),
      ).addModifiers(KModifier.PRIVATE)
      .superclass(ClassName("com.fasterxml.jackson.databind.introspect", "JacksonAnnotationIntrospector"))
      .addFunction(
        FunSpec
          .builder("findCreatorAnnotation")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter(
            "config",
            ClassName("com.fasterxml.jackson.databind.cfg", "MapperConfig").let {
              with(
                com.squareup.kotlinpoet.ParameterizedTypeName.Companion,
              ) { it.parameterizedBy(STAR) }
            },
          ).addParameter("value", ClassName("com.fasterxml.jackson.databind.introspect", "Annotated"))
          .returns(JACKSON_JSON_CREATOR.nestedClass("Mode").copy(nullable = true))
          .addCode(
            """
            if (value is %T && value.rawReturnType in types) return %T.DISABLED
            return super.findCreatorAnnotation(config, value)
            """.trimIndent() + "\n",
            ClassName("com.fasterxml.jackson.databind.introspect", "AnnotatedMethod"),
            JACKSON_JSON_CREATOR.nestedClass("Mode"),
          ).build(),
      ).build(),
  )
  registerSupport(support, helper, pairs.flatMap { listOf(it.first.value.first, it.second.value.first) })
}

// Only types reachable by this pair can participate in its Jackson merge operation.
private fun reachablePatchModels(
  roots: List<GeneratedModel>,
  models: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  allDeclarations: List<GeneratedModel>,
): Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>> {
  val declarations =
    io.outfoxx.sunday.generator.ir.emit
      .GeneratedModelIndex(allDeclarations)
  val reachable = mutableSetOf<GeneratedModel>()
  val pending = ArrayDeque(roots)

  fun visit(type: GeneratedTypeRef) {
    declarations.modelOrNull(type)?.let { pending.add(it) }
    type.arguments.forEach(::visit)
  }
  while (pending.isNotEmpty()) {
    val model = pending.removeFirst()
    if (!reachable.add(model)) continue
    model.properties.forEach { visit(it.type) }
    model.inherits.forEach(::visit)
    model.aliases.forEach(::visit)
    model.additionalProperties?.type?.let(::visit)
    model.patternProperties.forEach { visit(it.type) }
    model.discriminatorMappings.values.forEach(::visit)
    if (model.discriminator != null) {
      allDeclarations
        .filter { child ->
          child.inherits.any { declarations.modelOrNull(it) == model }
        }.forEach { pending.add(it) }
    }
  }
  return models.filterValues { it.first in reachable }
}
