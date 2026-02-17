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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.HttpStatus
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.DETAIL
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.INSTANCE
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.STATUS
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.TITLE
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.TYPE
import java.net.URI

enum class KotlinProblemLibrary(val id: String) {
  QUARKUS("quarkus"),
  SUNDAY("sunday"),
  ZALANDO("zalando"),
  ;

  fun support(rfc: KotlinProblemRfc): KotlinProblemLibrarySupport =
    when (this) {
      QUARKUS -> QuarkusProblemLibrarySupport(rfc)
      SUNDAY -> SundayProblemLibrarySupport(rfc)
      ZALANDO -> ZalandoProblemLibrarySupport(rfc)
    }
}

data class ProblemCustomProperty(
  val jsonName: String,
  val paramName: String,
  val typeName: TypeName,
)

interface KotlinProblemLibrarySupport {
  val rfc: KotlinProblemRfc
  val requiredFields: Set<ProblemField> get() = rfc.requiredFields
  val allowedFields: Set<ProblemField> get() = rfc.allowedFields
  val defaultFieldSet: Set<ProblemField> get() = rfc.defaultFieldSet
  val builderFieldMapping: Map<ProblemField, String>
  val throwableType: TypeName

  fun statusCodeAccess(varName: String): String

  fun configureProblemType(
    problemTypeBuilder: TypeSpec.Builder,
    problemTypeName: ClassName,
    problemTypeDefinition: ProblemTypeDefinition,
    customProperties: List<ProblemCustomProperty>,
    constructorBuilder: FunSpec.Builder,
  )

  fun validateRfcCompliance() {
    ProblemRfcCompliance.validate(
      rfc = rfc,
      builderFieldMapping = builderFieldMapping,
    )
  }
}

object ProblemRfcCompliance {
  fun validate(
    rfc: KotlinProblemRfc,
    builderFieldMapping: Map<ProblemField, String>,
  ) {
    require(rfc.requiredFields.all { it in rfc.allowedFields }) {
      "RFC ${rfc.id} required fields must be a subset of allowed fields"
    }
    require(rfc.defaultFieldSet.all { it in rfc.allowedFields }) {
      "RFC ${rfc.id} default field set must be a subset of allowed fields"
    }
    require(rfc.requiredFields.all { it in rfc.defaultFieldSet }) {
      "RFC ${rfc.id} required fields must be a subset of the default field set"
    }
    require(builderFieldMapping.keys.all { it in rfc.allowedFields }) {
      "Problem library defines field mappings that are not allowed by RFC ${rfc.id}"
    }
    require(rfc.defaultFieldSet.all { it in builderFieldMapping }) {
      "Problem library is missing field mappings required for RFC ${rfc.id}"
    }
  }
}

private class QuarkusProblemLibrarySupport(
  override val rfc: KotlinProblemRfc,
) : KotlinProblemLibrarySupport {

  override val builderFieldMapping: Map<ProblemField, String> =
    mapOf(
      TYPE to "withType",
      TITLE to "withTitle",
      STATUS to "withStatus",
      DETAIL to "withDetail",
      INSTANCE to "withInstance",
    )

  override val throwableType: TypeName = QUARKUS_HTTP_PROBLEM

  override fun statusCodeAccess(varName: String): String = "$varName.statusCode"

  override fun configureProblemType(
    problemTypeBuilder: TypeSpec.Builder,
    problemTypeName: ClassName,
    problemTypeDefinition: ProblemTypeDefinition,
    customProperties: List<ProblemCustomProperty>,
    constructorBuilder: FunSpec.Builder,
  ) {

    constructorBuilder
      .addParameter(
        ParameterSpec.builder(
          "instance",
          URI::class.asTypeName().copy(nullable = true),
        )
          .defaultValue("null")
          .build(),
      )
      .addParameter(
        ParameterSpec.builder(
          "cause",
          Throwable::class.asTypeName().copy(nullable = true),
        )
          .defaultValue("null")
          .build(),
      )

    problemTypeBuilder
      .superclass(QUARKUS_HTTP_PROBLEM)
      .primaryConstructor(constructorBuilder.build())
      .addSuperclassConstructorParameter("%L", buildBuilderCode(problemTypeDefinition, customProperties))

    problemTypeBuilder.addInitializerBlock(
      CodeBlock.builder()
        .beginControlFlow("if (cause != null)")
        .addStatement("initCause(cause)")
        .endControlFlow()
        .build(),
    )
  }

  private fun buildBuilderCode(
    problemTypeDefinition: ProblemTypeDefinition,
    customProperties: List<ProblemCustomProperty>,
  ): CodeBlock {
    val codeBuilder = CodeBlock.builder()
    codeBuilder.add("run {\n").indent()
    codeBuilder.addStatement("val builder = %T.builder()", QUARKUS_HTTP_PROBLEM)
    codeBuilder.addStatement("builder.withType(TYPE_URI)")
    codeBuilder.addStatement("builder.withTitle(%S)", problemTypeDefinition.title)
    codeBuilder.addStatement("builder.withStatus(%L)", problemTypeDefinition.status)
    codeBuilder.addStatement("builder.withDetail(%S)", problemTypeDefinition.detail)
    codeBuilder.beginControlFlow("if (instance != null)")
    codeBuilder.addStatement("builder.withInstance(instance)")
    codeBuilder.endControlFlow()
    customProperties.forEach { property ->
      if (property.typeName.isNullable) {
        codeBuilder.beginControlFlow("if (%N != null)", property.paramName)
        codeBuilder.addStatement("builder.with(%S, %N)", property.jsonName, property.paramName)
        codeBuilder.endControlFlow()
      } else {
        codeBuilder.addStatement("builder.with(%S, %N)", property.jsonName, property.paramName)
      }
    }
    codeBuilder.addStatement("builder")
    codeBuilder.unindent().add("}")
    return codeBuilder.build()
  }
}

private class SundayProblemLibrarySupport(
  override val rfc: KotlinProblemRfc,
) : KotlinProblemLibrarySupport {

  override val builderFieldMapping: Map<ProblemField, String> =
    mapOf(
      TYPE to "super(TYPE_URI)",
      TITLE to "super(title)",
      STATUS to "super(status)",
      DETAIL to "super(detail)",
      INSTANCE to "super(instance)",
    )

  override val throwableType: TypeName = SUNDAY_HTTP_PROBLEM

  override fun statusCodeAccess(varName: String): String = "$varName.status"

  override fun configureProblemType(
    problemTypeBuilder: TypeSpec.Builder,
    problemTypeName: ClassName,
    problemTypeDefinition: ProblemTypeDefinition,
    customProperties: List<ProblemCustomProperty>,
    constructorBuilder: FunSpec.Builder,
  ) {

    constructorBuilder
      .addParameter(
        ParameterSpec.builder(
          "instance",
          URI::class.asTypeName().copy(nullable = true),
        )
          .defaultValue("null")
          .build(),
      )

    problemTypeBuilder
      .superclass(SUNDAY_HTTP_PROBLEM)
      .primaryConstructor(constructorBuilder.build())
      .addSuperclassConstructorParameter("TYPE_URI")
      .addSuperclassConstructorParameter("%S", problemTypeDefinition.title)
      .addSuperclassConstructorParameter("%L", problemTypeDefinition.status)
      .addSuperclassConstructorParameter("%S", problemTypeDefinition.detail)
      .addSuperclassConstructorParameter("instance")
  }
}

private class ZalandoProblemLibrarySupport(
  override val rfc: KotlinProblemRfc,
) : KotlinProblemLibrarySupport {

  override val builderFieldMapping: Map<ProblemField, String> =
    mapOf(
      TYPE to "super(TYPE_URI)",
      TITLE to "super(title)",
      STATUS to "super(status)",
      DETAIL to "super(detail)",
      INSTANCE to "super(instance)",
    )

  override val throwableType: TypeName = ZALANDO_THROWABLE_PROBLEM

  override fun statusCodeAccess(varName: String): String = "$varName.status?.statusCode"

  override fun configureProblemType(
    problemTypeBuilder: TypeSpec.Builder,
    problemTypeName: ClassName,
    problemTypeDefinition: ProblemTypeDefinition,
    customProperties: List<ProblemCustomProperty>,
    constructorBuilder: FunSpec.Builder,
  ) {

    constructorBuilder
      .addParameter(
        ParameterSpec.builder(
          "instance",
          URI::class.asTypeName().copy(nullable = true),
        )
          .defaultValue("null")
          .build(),
      )
      .addParameter(
        ParameterSpec.builder(
          "cause",
          ZALANDO_THROWABLE_PROBLEM.copy(nullable = true),
        )
          .defaultValue("null")
          .build(),
      )

    problemTypeBuilder
      .superclass(ZALANDO_ABSTRACT_THROWABLE_PROBLEM)
      .primaryConstructor(constructorBuilder.build())
      .addSuperclassConstructorParameter("TYPE_URI")
      .addSuperclassConstructorParameter("%S", problemTypeDefinition.title)
      .addSuperclassConstructorParameter(
        "%T.%L",
        ZALANDO_STATUS,
        HttpStatus.valueOf(problemTypeDefinition.status).name,
      )
      .addSuperclassConstructorParameter("%S", problemTypeDefinition.detail)
      .addSuperclassConstructorParameter("instance")
      .addSuperclassConstructorParameter("cause")
      .addFunction(
        FunSpec.builder("getCause")
          .addAnnotation(JACKSON_JSON_IGNORE)
          .returns(ZALANDO_EXCEPTIONAL.copy(nullable = true))
          .addModifiers(com.squareup.kotlinpoet.KModifier.OVERRIDE)
          .addCode("return super.cause")
          .build(),
      )
  }
}
