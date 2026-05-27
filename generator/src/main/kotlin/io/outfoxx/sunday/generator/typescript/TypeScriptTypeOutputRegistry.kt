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

package io.outfoxx.sunday.generator.typescript

import io.outfoxx.typescriptpoet.AnyTypeSpecBuilder
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.TypeName

/**
 * TypeScript type registry surface required by IR-backed emitters.
 */
interface TypeScriptTypeOutputRegistry {

  fun generatedTypeName(
    simpleName: String,
    modulePath: String,
  ): TypeName.Standard

  fun addServiceType(
    typeName: TypeName.Standard,
    serviceType: AnyTypeSpecBuilder,
    extras: List<Any> = listOf(),
  )

  fun addModelType(
    typeName: TypeName.Standard,
    modelType: AnyTypeSpecBuilder,
    extras: List<Any> = listOf(),
  )

  fun addCompanionSchemaType(typeName: TypeName.Standard)

  fun addCompanionSchemaCode(
    typeName: TypeName.Standard,
    member: CodeBlock,
  )

  fun schemaInitializer(typeName: TypeName): CodeBlock

  fun runtimeSchemaForType(
    typeName: TypeName,
    runtimeName: String,
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock
}
