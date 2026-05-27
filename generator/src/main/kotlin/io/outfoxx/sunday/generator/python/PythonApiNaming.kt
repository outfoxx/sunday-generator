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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

internal fun GeneratedApi.pythonPackageName(options: PythonGeneratorOptions): String =
  options.packageName?.pythonIdentifierName
    ?: targets["python"]?.packageName?.pythonIdentifierName
    ?: targets["python"]?.moduleName?.pythonIdentifierName
    ?: name.pythonIdentifierName

internal val GeneratedApi.aggregateTypeName: String
  get() = name.toUpperCamelCase()

internal val GeneratedApi.aggregateIdentifierName: String
  get() = aggregateTypeName.pythonIdentifierName
