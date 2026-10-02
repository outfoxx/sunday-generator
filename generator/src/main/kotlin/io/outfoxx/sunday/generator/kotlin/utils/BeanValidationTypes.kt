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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

class BeanValidationTypes(
  private val basePackage: String,
) {

  val valid = ClassName.bestGuess("$basePackage.validation.Valid")
  val decimalMax = ClassName.bestGuess("$basePackage.validation.constraints.DecimalMax")
  val decimalMin = ClassName.bestGuess("$basePackage.validation.constraints.DecimalMin")
  val email = ClassName.bestGuess("$basePackage.validation.constraints.Email")
  val max = ClassName.bestGuess("$basePackage.validation.constraints.Max")
  val min = ClassName.bestGuess("$basePackage.validation.constraints.Min")
  val notNull = ClassName.bestGuess("$basePackage.validation.constraints.NotNull")
  val pattern = ClassName.bestGuess("$basePackage.validation.constraints.Pattern")
  val size = ClassName.bestGuess("$basePackage.validation.constraints.Size")
  val requestMode = ClassName("io.outfoxx.sunday.validation.$basePackage", "ModelMode", "Request")
  val responseMode = ClassName("io.outfoxx.sunday.validation.$basePackage", "ModelMode", "Response")
  val acyclic = ClassName("io.outfoxx.sunday.validation.$basePackage", "Acyclic")
  val serializableModel = ClassName("io.outfoxx.sunday.validation.$basePackage", "SerializableModel")
  val entitySchema = ClassName("io.outfoxx.sunday.validation.$basePackage", "EntitySchema")
  val schema = ClassName("io.outfoxx.sunday.validation.$basePackage", "Schema")
  val cascadedValues = ClassName("io.outfoxx.sunday.validation.$basePackage", "CascadedValues")
  val dynamicProperties = ClassName("io.outfoxx.sunday.validation.$basePackage", "DynamicProperties")
  val dynamicModel = ClassName("io.outfoxx.sunday.validation.$basePackage", "DynamicModel")
  val clientModelValidation = ClassName("io.outfoxx.sunday.validation.$basePackage", "ClientModelValidation")
  val modelValidation = ClassName("io.outfoxx.sunday.validation.$basePackage", "ModelValidation")
  val constraintViolationException = ClassName("$basePackage.validation", "ConstraintViolationException")

  /** Converts the framework's default validation group at a request entity boundary. */
  fun requestGroupConversion(): AnnotationSpec =
    AnnotationSpec
      .builder(ClassName("$basePackage.validation.groups", "ConvertGroup"))
      .addMember("to = %T::class", requestMode)
      .build()

  /** Native class constraint attached to fallback implementations for request validation. */
  fun knownVariant(): AnnotationSpec =
    AnnotationSpec
      .builder(ClassName("io.outfoxx.sunday.validation.$basePackage", "KnownVariant"))
      .addMember("groups = [%T::class]", requestMode)
      .build()

  companion object {

    val JAVAX = BeanValidationTypes("javax")
    val JAKARTA = BeanValidationTypes("jakarta")
  }
}
