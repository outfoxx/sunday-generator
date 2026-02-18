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

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver

class ResourceExtension : ParameterResolver {

  companion object {

    fun get(name: String) =
      Thread
        .currentThread()
        .contextClassLoader
        .getResource(name)
        ?.toURI()
        ?: throw ParameterResolutionException("Unable to find test resource '$name'")
  }

  override fun supportsParameter(
    parameterContext: ParameterContext,
    extensionContext: ExtensionContext,
  ): Boolean = parameterContext.isAnnotated(ResourceUri::class.java)

  override fun resolveParameter(
    parameterContext: ParameterContext,
    extensionContext: ExtensionContext,
  ): Any {

    val resAnn = parameterContext.findAnnotation(ResourceUri::class.java).orElse(null)
    if (resAnn != null) {
      return get(resAnn.value)
    }

    throw ParameterResolutionException("parameter missing a valid resource uri")
  }
}
