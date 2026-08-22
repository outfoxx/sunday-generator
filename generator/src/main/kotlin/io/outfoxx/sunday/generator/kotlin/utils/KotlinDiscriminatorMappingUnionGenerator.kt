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
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedDiscriminatorFallback
import io.outfoxx.sunday.generator.ir.emit.modelOrNull
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

internal class KotlinDiscriminatorMappingUnionGenerator(
  private val api: GeneratedApi,
  private val apiIndex: GeneratedApiIndex,
  private val discriminatorFallbacks: Map<GeneratedModel, GeneratedDiscriminatorFallback>,
  private val jacksonAnnotations: Boolean,
  private val typeName: (GeneratedModel) -> ClassName,
) {

  fun isUnion(model: GeneratedModel): Boolean =
    model.kind == GeneratedModel.Kind.OBJECT &&
      model.discriminatorMappings.isNotEmpty() &&
      mappingModels(model).any { mappedModel -> !structurallyInherits(mappedModel, model) }

  fun supertypesOf(model: GeneratedModel): List<GeneratedModel> =
    api.models.filter { union ->
      isUnion(union) &&
        union.discriminatorMappings.values.any { mappedType -> mappedType.modelOrNull(apiIndex) == model }
    }

  fun inheritedSupertypesOf(model: GeneratedModel): List<GeneratedModel> =
    model.inherits.mapNotNull { inherited ->
      inherited.modelOrNull(apiIndex)?.takeIf(::isUnion)
    }

  fun generateOrNull(
    model: GeneratedModel,
    directUnionSupertypes: List<GeneratedModel>,
    addJacksonPolymorphism: TypeSpec.Builder.(GeneratedModel) -> Unit,
  ): TypeSpec.Builder? {
    if (!isUnion(model)) {
      return null
    }

    val unionTypeName = typeName(model)
    return TypeSpec
      .interfaceBuilder(unionTypeName)
      .addModifiers(KModifier.PUBLIC)
      .apply {
        if (canSeal(model)) {
          addModifiers(KModifier.SEALED)
        }
        directUnionSupertypes.forEach { union ->
          addSuperinterface(typeName(union))
        }
        supertypesOf(model).forEach { union ->
          addSuperinterface(typeName(union))
        }
        if (jacksonAnnotations) {
          if (model.discriminator != null) {
            addAnnotation(
              AnnotationSpec
                .builder(JACKSON_JSON_DESERIALIZE)
                .addMember("using = %T::class", unionTypeName.nestedClass("Deserializer"))
                .build(),
            )
            addType(deserializerType(model, unionTypeName))
          } else {
            addJacksonPolymorphism(model)
          }
        }
      }
  }

  private fun deserializerType(
    model: GeneratedModel,
    unionTypeName: ClassName,
  ): TypeSpec {
    val discriminatorName =
      model.discriminator ?: genError("Discriminator mapping union '${model.name}' has no discriminator")
    val discriminatorWireName =
      discriminatorPropertyOrNull(model, discriminatorName)?.serializationName ?: discriminatorName
    val mappedTypes =
      model.discriminatorMappings.mapNotNull { (value, mappedType) ->
        val mappedModel = mappedType.modelOrNull(apiIndex) ?: return@mapNotNull null
        value to typeName(mappedModel)
      }
    val deserialize =
      FunSpec
        .builder("deserialize")
        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
        .addParameter("parser", JACKSON_JSON_PARSER)
        .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
        .returns(unionTypeName)
        .addStatement("val tree = parser.codec.readTree<%T>(parser)", JSON_NODE)
        .addStatement("val discriminatorValue = tree.get(%S)?.asText()", discriminatorWireName)
        .apply {
          mappedTypes.forEach { (value, mappedTypeName) ->
            beginControlFlow("if (discriminatorValue == %S)", value)
            addStatement("return parser.codec.treeToValue(tree, %T::class.java)", mappedTypeName)
            endControlFlow()
          }
          val fallback = discriminatorFallbacks[model]
          if (fallback == null) {
            addStatement(
              "throw %T.from(parser, %S)",
              JACKSON_JSON_MAPPING_EXCEPTION,
              "unsupported value for \"$discriminatorWireName\"",
            )
          } else {
            beginControlFlow(
              "if (tree.get(%S) == null || !tree.get(%S).isTextual)",
              discriminatorWireName,
              discriminatorWireName,
            )
            addStatement(
              "throw %T.from(parser, %S)",
              JACKSON_JSON_MAPPING_EXCEPTION,
              "missing or non-string value for \"$discriminatorWireName\"",
            )
            endControlFlow()
            val fallbackTypeName = ClassName(unionTypeName.packageName, fallback.modelName.toUpperCamelCase())
            addStatement("return parser.codec.treeToValue(tree, %T::class.java)", fallbackTypeName)
          }
        }.build()

    return TypeSpec
      .classBuilder("Deserializer")
      .addModifiers(KModifier.PUBLIC)
      .superclass(JACKSON_JSON_DESERIALIZER.parameterizedBy(unionTypeName))
      .addFunction(deserialize)
      .build()
  }

  private fun canSeal(model: GeneratedModel): Boolean {
    val packageName = typeName(model).packageName
    val implementations = mappingModels(model) + directInheritors(model)
    return implementations.all { implementation -> typeName(implementation).packageName == packageName }
  }

  private fun mappingModels(model: GeneratedModel): List<GeneratedModel> =
    model.discriminatorMappings.values.mapNotNull { mappedType -> mappedType.modelOrNull(apiIndex) }

  private fun structurallyInherits(
    model: GeneratedModel,
    base: GeneratedModel,
  ): Boolean =
    model.inherits.any { inherited ->
      val inheritedModel = inherited.modelOrNull(apiIndex) ?: return@any false
      inheritedModel == base || structurallyInherits(inheritedModel, base)
    }

  private fun directInheritors(model: GeneratedModel): List<GeneratedModel> =
    api.models.filter { candidate ->
      candidate.inherits.any { inherited -> inherited.modelOrNull(apiIndex) == model }
    }

  private fun discriminatorPropertyOrNull(
    model: GeneratedModel,
    discriminatorName: String,
  ): GeneratedModelProperty? =
    model.properties.firstOrNull { property -> property.name == discriminatorName }
      ?: model.inherits.firstNotNullOfOrNull { inherited ->
        inherited.modelOrNull(apiIndex)?.let { inheritedModel ->
          discriminatorPropertyOrNull(inheritedModel, discriminatorName)
        }
      }
}
