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

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode

/** Publishes contract defaults below application configuration without writing application.properties. */
internal object KotlinQuarkusConfiguration {
  fun register(
    registry: KotlinTypeOutputRegistry,
    name: ClassName,
    defaults: Map<String, String>,
  ) {
    val source = ClassName("org.eclipse.microprofile.config.spi", "ConfigSource")
    val values = MAP.parameterizedBy(STRING, STRING)
    val type =
      TypeSpec
        .classBuilder(name)
        .addKdoc("Overridable native Quarkus defaults; deployment supplies endpoints, trust, and credentials.\n")
        .addSuperinterface(source)
        .addProperty(
          PropertySpec
            .builder("values", values, KModifier.PRIVATE)
            .initializer(
              "mapOf(%L)",
              defaults
                .map { (key, value) ->
                  CodeBlock.of("%S to %S", key, value)
                }.joinToCode(",\n"),
            ).build(),
        ).addFunction(
          FunSpec
            .builder(
              "getName",
            ).addModifiers(KModifier.OVERRIDE)
            .returns(STRING)
            .addStatement("return %S", name.canonicalName)
            .build(),
        ).addFunction(
          FunSpec
            .builder("getOrdinal")
            .addModifiers(KModifier.OVERRIDE)
            .returns(INT)
            .addStatement("return 100")
            .build(),
        ).addFunction(
          FunSpec
            .builder(
              "getProperties",
            ).addModifiers(KModifier.OVERRIDE)
            .returns(values)
            .addStatement("return values")
            .build(),
        ).addFunction(
          FunSpec
            .builder(
              "getPropertyNames",
            ).addModifiers(
              KModifier.OVERRIDE,
            ).returns(SET.parameterizedBy(STRING))
            .addStatement("return values.keys")
            .build(),
        ).addFunction(
          FunSpec
            .builder(
              "getValue",
            ).addModifiers(
              KModifier.OVERRIDE,
            ).addParameter("name", STRING)
            .returns(STRING.copy(nullable = true))
            .addStatement("return values[name]")
            .build(),
        )
    registry.addServiceType(name, type)
    registry.addServiceProvider(source, name)
  }
}
