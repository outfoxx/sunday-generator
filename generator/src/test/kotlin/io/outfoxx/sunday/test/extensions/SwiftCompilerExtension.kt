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
      SwiftCompiler(workDir)
    }
  }

}
