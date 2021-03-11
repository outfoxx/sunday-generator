package io.outfoxx.sunday.test.extensions

import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.nio.file.Files

class TypeScriptCompilerExtension : ParameterResolver {

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == TypeScriptCompiler::class.java

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {

    val store = extensionContext.root.getStore(ExtensionContext.Namespace.GLOBAL)

    val workDir = store.getOrComputeIfAbsent(TempDir::class.java).path.resolve("typescript")
    Files.createDirectories(workDir)

    return store.getOrComputeIfAbsent(TypeScriptCompiler::class.java) {
      TypeScriptCompiler(workDir)
    }
  }

}
