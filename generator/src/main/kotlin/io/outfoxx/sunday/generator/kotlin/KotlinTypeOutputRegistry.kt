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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.utils.BeanValidationTypes
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrarySupport

/**
 * Kotlin type registry surface required by IR-backed emitters.
 */
interface KotlinTypeOutputRegistry {

  val defaultModelPackageName: String?
  val generationMode: GenerationMode
  val options: Set<KotlinTypeRegistry.Option>
  val problemLibrary: KotlinProblemLibrary
  val problemLibrarySupport: KotlinProblemLibrarySupport
  val beanValidationTypes: BeanValidationTypes

  fun addServiceType(
    className: ClassName,
    serviceType: TypeSpec.Builder,
  )

  fun addModelType(
    className: ClassName,
    modelType: TypeSpec.Builder,
  )

  fun addGeneratedTo(
    builder: TypeSpec.Builder,
    verbose: Boolean,
  ): TypeSpec.Builder
}
