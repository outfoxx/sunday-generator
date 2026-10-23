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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.kotlin.utils.JSON_NODE
import io.outfoxx.sunday.generator.kotlin.utils.JaxRsTypes

/** Preserves entity presence across decoding for optional, non-nullable request schemas. */
internal object KotlinJAXRSOptionalBodyReader {
  fun typeName(service: ClassName): ClassName = service.peerClass("${service.simpleName}OptionalBodyReader")

  fun binding(service: ClassName): ClassName = typeName(service).nestedClass("NonNull")

  fun generate(
    service: ClassName,
    types: JaxRsTypes,
    jsonBody: Boolean,
    support: ClassName,
  ): TypeSpec.Builder {
    val namespace = types.path.packageName
    val binding = binding(service)
    return TypeSpec
      .classBuilder(typeName(service))
      .addKdoc("Rejects a supplied null entity without rejecting an omitted request body.\n")
      .addAnnotation(ClassName("$namespace.ext", "Provider"))
      .addAnnotation(
        AnnotationSpec
          .builder(ClassName(namespace, "ConstrainedTo"))
          .addMember("%T.SERVER", ClassName(namespace, "RuntimeType"))
          .build(),
      ).addSuperinterface(ClassName("$namespace.ext", "ReaderInterceptor"))
      .addType(
        TypeSpec
          .annotationBuilder(binding)
          .addKdoc("Binds presence-aware decoding to optional bodies whose schemas forbid null.\n")
          .addAnnotation(
            AnnotationSpec
              .builder(
                Target::class,
              ).addMember("AnnotationTarget.VALUE_PARAMETER")
              .build(),
          ).addAnnotation(AnnotationSpec.builder(Retention::class).addMember("AnnotationRetention.RUNTIME").build())
          .build(),
      ).addFunction(
        FunSpec
          .builder("aroundReadFrom")
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Records wire presence before the reader can collapse an empty body and JSON null.\n")
          .addParameter("context", ClassName("$namespace.ext", "ReaderInterceptorContext"))
          .returns(ANY.copy(nullable = true))
          .addStatement(
            "return %T.read(context, %T::class.java) { value -> %L }",
            support,
            binding,
            if (jsonBody) {
              CodeBlock.of("value == null || value is %T && value.isNull", JSON_NODE)
            } else {
              CodeBlock.of("value == null")
            },
          ).build(),
      )
  }

  /** Emits the package implementation; providers retain their own marker and codec-specific null checks. */
  fun support(
    name: ClassName,
    types: JaxRsTypes,
  ): TypeSpec.Builder {
    val namespace = types.path.packageName
    return TypeSpec
      .objectBuilder(name)
      .addModifiers(KModifier.INTERNAL)
      .addKdoc("Shared entity-presence handling for generated optional-body readers.\n")
      .addFunction(
        FunSpec
          .builder("read")
          .addParameter("context", ClassName("$namespace.ext", "ReaderInterceptorContext"))
          .addParameter("binding", ClassName("java.lang", "Class").parameterizedBy(STAR))
          .addParameter(
            "isNull",
            LambdaTypeName.get(parameters = arrayOf(ANY.copy(nullable = true)), returnType = BOOLEAN),
          ).returns(ANY.copy(nullable = true))
          .addCode(
            """
            if (context.annotations.none { binding.isInstance(it) }) return context.proceed()
            val stream = %T(context.inputStream, 1)
            val first = stream.read()
            if (first != -1) stream.unread(first)
            context.inputStream = stream
            val value = context.proceed()
            if (first != -1 && isNull(value)) {
              throw %T("Request body must not be null")
            }
            return value
            """.trimIndent().replace(' ', '·'),
            ClassName("java.io", "PushbackInputStream"),
            ClassName(namespace, "BadRequestException"),
          ).build(),
      )
  }
}
