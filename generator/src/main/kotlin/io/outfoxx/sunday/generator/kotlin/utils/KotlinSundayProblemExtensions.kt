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
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.MUTABLE_SET
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT

/** Shares Sunday runtime extension storage while retaining generated codecs and inherited field guards. */
internal fun TypeSpec.Builder.addSundayProblemExtensions(
  owner: ClassName,
  binding: ClassName,
  setterName: String?,
  fieldName: String?,
  omittedNullProperties: Set<String>,
  nestedModelNames: Set<String>,
  register: (ClassName, TypeSpec.Builder) -> Unit,
) {
  val names = NameAllocator()
  (typeSpecs.mapNotNull { it.name } + nestedModelNames).distinct().forEach { names.newName(it) }
  val storage = owner.nestedClass(names.newName("RuntimeExtensionStorage"))
  val any = ANY.copy(nullable = true)
  val map = MUTABLE_MAP.parameterizedBy(STRING, any)
  val entryType = MUTABLE_MAP.nestedClass("MutableEntry").parameterizedBy(STRING, any)
  val iteratorType = ClassName("kotlin.collections", "MutableIterator").parameterizedBy(entryType)
  // Jackson inherits interface annotations before superclass annotations. The runtime methods are final,
  // so this contract masks only their wire bindings without overriding their implementation.
  addSuperinterface(binding)
  register(
    binding,
    TypeSpec
      .interfaceBuilder(binding)
      .apply {
        for (name in setOf("type", "title", "status", "detail", "instance")) {
          val type =
            when (name) {
              "type", "instance" -> ClassName("java.net", "URI")
              "status" -> INT
              else -> STRING
            }
          addProperty(
            PropertySpec
              .builder(name, type.copy(nullable = true))
              .addModifiers(KModifier.ABSTRACT)
              .addKdoc("Binds the inherited problem field to its JSON property.\n")
              .addAnnotation(
                AnnotationSpec
                  .builder(
                    JACKSON_JSON_PROPERTY,
                  ).useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                  .addMember("%S", name)
                  .build(),
              ).apply {
                if (name in omittedNullProperties) {
                  addAnnotation(
                    AnnotationSpec
                      .builder(
                        JACKSON_JSON_INCLUDE,
                      ).useSiteTarget(
                        AnnotationSpec.UseSiteTarget.GET,
                      ).addMember("%T.NON_NULL", JACKSON_JSON_INCLUDE_INCLUDE)
                      .build(),
                  )
                }
              }.build(),
          )
        }
      }.addKdoc("Keeps runtime extension APIs from competing with this model's schema-aware Jackson bindings.\n")
      .addFunction(
        FunSpec
          .builder("extension")
          .addModifiers(KModifier.ABSTRACT)
          .addKdoc("Uses the generated schema-aware setter for JSON fields.\n")
          .addAnnotation(
            AnnotationSpec
              .builder(ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter"))
              .addMember("enabled = false")
              .build(),
          ).addParameter("name", STRING)
          .addParameter("value", any)
          .build(),
      ).addFunction(
        FunSpec
          .builder("extensions")
          .addModifiers(KModifier.ABSTRACT)
          .addKdoc("Uses the generated getter to preserve explicit null extension values.\n")
          .addAnnotation(
            AnnotationSpec
              .builder(ClassName("com.fasterxml.jackson.annotation", "JsonAnyGetter"))
              .addMember("enabled = false")
              .build(),
          ).returns(MAP.parameterizedBy(STRING, any))
          .build(),
      ),
  )
  // Without a generated handler, leave unknown-field decisions to Jackson instead of the runtime catch-all.
  if (setterName == null) return
  addSuperclassConstructorParameter("extensions = %T()", storage)
  // Existing generated accessors and setters use this backing map. The runtime receives a guarded view
  // so its final extension() API cannot bypass declared-field or closed-descendant checks.
  propertySpecs.replaceAll { property ->
    if (property.name == fieldName) {
      property.toBuilder().initializer("(extensions as %T).fields", storage).build()
    } else {
      property
    }
  }
  addInitializerBlock(
    CodeBlock.of("(extensions as %T).write = ::%N\n", storage, setterName),
  )
  addType(
    TypeSpec
      .classBuilder(storage)
      .addModifiers(KModifier.PRIVATE)
      .addKdoc("Routes runtime map writes through the active generated model's extension setter.\n")
      .superclass(ClassName("kotlin.collections", "AbstractMutableMap").parameterizedBy(STRING, any))
      .addProperty(PropertySpec.builder("fields", map).initializer("linkedMapOf()").build())
      .addProperty(
        PropertySpec
          .builder("write", LambdaTypeName.get(parameters = arrayOf(STRING, any), returnType = UNIT))
          .mutable()
          .addModifiers(KModifier.LATEINIT)
          .build(),
      ).addProperty(
        PropertySpec
          .builder(
            "entries",
            MUTABLE_SET.parameterizedBy(entryType),
          ).addModifiers(KModifier.OVERRIDE)
          .getter(
            FunSpec
              .getterBuilder()
              .addCode(
                """
                return object : %T() {
                  override val size: %T get() = fields.size

                  override fun add(element: %T): %T =
                    throw %T("Entries cannot be added directly")

                  override fun iterator(): %T {
                    val iterator = fields.entries.iterator()
                    return object : %T by iterator {
                      override fun next(): %T {
                        val entry = iterator.next()
                        return object : %T by entry {
                          override fun setValue(newValue: %T): %T = this@%N.put(key, newValue)
                          override fun equals(other: %T): %T = entry == other
                          override fun hashCode(): %T = entry.hashCode()
                          override fun toString(): %T = entry.toString()
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
                ClassName("kotlin.collections", "AbstractMutableSet").parameterizedBy(entryType),
                INT,
                entryType,
                BOOLEAN,
                UnsupportedOperationException::class,
                iteratorType,
                iteratorType,
                entryType,
                entryType,
                any,
                any,
                storage.simpleName,
                any,
                BOOLEAN,
                INT,
                STRING,
              ).build(),
          ).build(),
      ).addFunction(
        FunSpec
          .builder("put")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("key", STRING)
          .addParameter("value", any)
          .returns(any)
          .addStatement("val previous = fields[key]")
          .addStatement("write(key, value)")
          .addStatement("return previous")
          .build(),
      ).build(),
  )
}
