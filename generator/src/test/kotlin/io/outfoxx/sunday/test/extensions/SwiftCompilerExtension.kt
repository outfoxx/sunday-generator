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

package io.outfoxx.sunday.test.extensions

import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.nio.file.Files

class SwiftCompilerExtension : ParameterResolver {

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == SwiftCompiler::class.java

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {

    val store = extensionContext.root.getStore(ExtensionContext.Namespace.GLOBAL)

    val workDir = store.getOrComputeIfAbsent(TempDir::class.java).path.resolve("swift")
    Files.createDirectories(workDir)

    return store.getOrComputeIfAbsent(SwiftCompiler::class.java) {
      SwiftCompiler.create(workDir)
    }
  }
}
