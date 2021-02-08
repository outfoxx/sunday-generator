package io.outfoxx.sunday.test.extensions

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver

class ResourceExtension : ParameterResolver {

  companion object {

    fun get(name: String) =
      Thread.currentThread().contextClassLoader.getResource(name)?.toURI()
        ?: throw ParameterResolutionException("Unable to find test resource '$name'")

  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.isAnnotated(ResourceUri::class.java)

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {

    val resAnn = parameterContext.findAnnotation(ResourceUri::class.java).orElse(null)
    if (resAnn != null) {
      return get(resAnn.value)
    }

    throw ParameterResolutionException("parameter missing a valid resource uri")
  }

}
