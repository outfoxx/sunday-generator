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

class BeanValidationTypes(basePackage: String) {

  val valid = ClassName.bestGuess("$basePackage.validation.Valid")
  val decimalMax = ClassName.bestGuess("$basePackage.validation.constraints.DecimalMax")
  val decimalMin = ClassName.bestGuess("$basePackage.validation.constraints.DecimalMin")
  val max = ClassName.bestGuess("$basePackage.validation.constraints.Max")
  val min = ClassName.bestGuess("$basePackage.validation.constraints.Min")
  val pattern = ClassName.bestGuess("$basePackage.validation.constraints.Pattern")
  val size = ClassName.bestGuess("$basePackage.validation.constraints.Size")

  companion object {

    val JAVAX = BeanValidationTypes("javax")
    val JAKARTA = BeanValidationTypes("jakarta")
  }
}
