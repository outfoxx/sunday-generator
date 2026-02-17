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

package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.swiftpoet.ARRAY
import io.outfoxx.swiftpoet.GenericQualifiedTypeName
import io.outfoxx.swiftpoet.ParameterizedTypeName
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.parameterizedBy

/**
 * Extension methods for SwiftPoet classes/types
 */

fun TypeName.array(): TypeName = ARRAY.parameterizedBy(this)

fun TypeName.concreteType(): TypeName =
  when (val unwrapped = makeNonOptional().makeNonImplicit()) {
    is ParameterizedTypeName -> unwrapped.rawType.makeNonOptional().makeNonImplicit()
    is GenericQualifiedTypeName -> unwrapped.type.makeNonOptional().makeNonImplicit()
    else -> unwrapped
  }
