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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNominalTypes

/** Builds nominal scalars without JVM value-class mangling, including JAX-RS string conversion. */
internal class KotlinNominalTypes(
  private val models: List<GeneratedModel>,
  private val nominal: GeneratedNominalTypes,
  private val properties: GeneratedModelProperties,
  private val jackson: Boolean,
  private val modelName: (GeneratedModel) -> ClassName,
  private val typeName: (GeneratedTypeRef) -> TypeName,
) {
  fun generate(model: GeneratedModel): TypeSpec.Builder? =
    when {
      model.nominal -> scalar(model)
      nominal.branches(model).isNotEmpty() -> union(model)
      else -> null
    }

  private fun scalar(model: GeneratedModel): TypeSpec.Builder {
    val scalar = nominal.scalar(model)
    val name = modelName(model)
    val raw = typeName(scalar.type)
    val parents = models.filter { model in nominal.branches(it) }
    val field = GeneratedModelProperties.Field(scalar.property, scalar.property, false)
    return TypeSpec
      .classBuilder(name)
      .addKdoc("Validated scalar value for %L.\n", model.name)
      .addModifiers(KModifier.DATA)
      .primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addParameter("value", raw)
          .apply {
            if (jackson) {
              addAnnotation(
                AnnotationSpec
                  .builder(JACKSON_JSON_CREATOR)
                  .addMember("mode = %T.DELEGATING", JACKSON_JSON_CREATOR.nestedClass("Mode"))
                  .build(),
              )
            }
          }.build(),
      ).addProperty(
        PropertySpec
          .builder("value", raw)
          .initializer("value")
          .apply {
            if (parents.isNotEmpty()) addModifiers(KModifier.OVERRIDE)
            if (jackson) {
              addAnnotation(
                AnnotationSpec
                  .builder(JACKSON_JSON_VALUE)
                  .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                  .build(),
              )
            }
          }.build(),
      ).apply {
        parents.forEach { addSuperinterface(modelName(it)) }
        if (jackson && parents.isNotEmpty()) {
          addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_DESERIALIZE)
              .addMember("using = %T::class", JACKSON_JSON_DESERIALIZER_NONE)
              .build(),
          )
        }
      }.addInitializerBlock(
        CodeBlock
          .builder()
          .add(KotlinModelConstraints.initializer(listOf(field), properties, typeName = typeName))
          .apply {
            scalar.patterns.filterNot { it == scalar.property.validation["pattern"] }.forEach {
              addStatement(
                "require(%T(%S).containsMatchIn(value)) { %S }",
                Regex::class,
                it,
                "Invalid value for '${model.name}'",
              )
            }
          }.build(),
      ).addFunction(
        FunSpec
          .builder("toString")
          .addModifiers(KModifier.OVERRIDE)
          .returns(STRING)
          .addStatement(if (raw == STRING) "return value" else "return value.toString()")
          .build(),
      ).addType(TypeSpec.companionObjectBuilder().addFunction(fromString(name, raw, "return %T(%L)", name)).build())
  }

  private fun union(model: GeneratedModel): TypeSpec.Builder {
    val name = modelName(model)
    val raw = typeName(nominal.unionType(model))
    val factory =
      FunSpec
        .builder("fromValue")
        .addAnnotation(JvmStatic::class)
        .addKdoc("Selects a validated branch using the source union's matching rule.\n")
        .addParameter("value", raw)
        .returns(name)
        .addStatement("val matches = mutableListOf<%T>()", name)
    nominal.branches(model).forEach { branch ->
      factory.addStatement("runCatching { %T(value) }.getOrNull()?.let(matches::add)", modelName(branch))
    }
    factory.addStatement("require(matches.isNotEmpty()) { %S }", "No branch matched ${model.name}")
    if (model.unionMode == GeneratedModel.UnionMode.ONE_OF) {
      factory.addStatement(
        "require(matches.size == 1) { %S }",
        "Ambiguous value for ${model.name}: multiple branches matched",
      )
    }
    factory.addStatement("return matches.first()")
    val builder =
      TypeSpec
        .interfaceBuilder(name)
        .addModifiers(KModifier.SEALED)
        .addKdoc("Validated scalar union %L.\n", model.name)
        .addProperty(PropertySpec.builder("value", raw).build())
        .addType(
          TypeSpec
            .companionObjectBuilder()
            .addFunction(factory.build())
            .addFunction(fromString(name, raw, "return %T.fromValue(%L)", name))
            .build(),
        )
    if (jackson) {
      val decoder = name.nestedClass("Deserializer")
      builder.addAnnotation(
        AnnotationSpec
          .builder(JACKSON_JSON_DESERIALIZE)
          .addMember("using = %T::class", decoder)
          .build(),
      )
      builder.addType(
        TypeSpec
          .classBuilder(decoder)
          .addKdoc("Decodes the scalar once before matching branches.\n")
          .superclass(JACKSON_JSON_DESERIALIZER.parameterizedBy(name))
          .addFunction(
            FunSpec
              .builder("deserialize")
              .addModifiers(KModifier.OVERRIDE)
              .addParameter("parser", JACKSON_JSON_PARSER)
              .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
              .returns(name)
              .addStatement("val value = parser.readValueAs(%T::class.javaObjectType)", raw)
              .beginControlFlow("try")
              .addStatement("return fromValue(value)")
              .nextControlFlow("catch (error: %T)", IllegalArgumentException::class)
              .addStatement("throw %T.from(parser, error.message, error)", JACKSON_JSON_MAPPING_EXCEPTION)
              .endControlFlow()
              .build(),
          ).build(),
      )
    }
    return builder
  }

  private fun fromString(
    name: ClassName,
    raw: TypeName,
    statement: String,
    target: ClassName,
  ): FunSpec {
    val conversion =
      when (raw.toString()) {
        "kotlin.String" -> CodeBlock.of("value")
        "kotlin.Boolean" -> CodeBlock.of("value.toBooleanStrict()")
        "kotlin.Byte" -> CodeBlock.of("value.toByte()")
        "kotlin.Short" -> CodeBlock.of("value.toShort()")
        "kotlin.Int" -> CodeBlock.of("value.toInt()")
        "kotlin.Long" -> CodeBlock.of("value.toLong()")
        "kotlin.Float" -> CodeBlock.of("value.toFloat()")
        "kotlin.Double" -> CodeBlock.of("value.toDouble()")
        else -> CodeBlock.of("%T(value)", raw)
      }
    return FunSpec
      .builder("fromString")
      .addAnnotation(JvmStatic::class)
      .addKdoc("Parses a wire value, including JAX-RS path and query parameters.\n")
      .addParameter("value", STRING)
      .returns(name)
      .addStatement(statement, target, conversion)
      .build()
  }
}
