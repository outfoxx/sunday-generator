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

package io.outfoxx.sunday.generator.kotlin.tools

import java.io.File
import java.net.URLClassLoader

/** Runs a compiled model against the selected provider without mixing javax and Jakarta engines. */
internal fun nativeConstraintPaths(
  namespace: String,
  value: Any,
  mode: String,
): Set<String> {
  val classpath = requireNotNull(System.getProperty("sunday.validation.$namespace.classpath"))
  val urls = classpath.split(File.pathSeparator).map { File(it).toURI().toURL() }.toTypedArray()
  return URLClassLoader(urls, value.javaClass.classLoader).use { loader ->
    val thread = Thread.currentThread()
    val previous = thread.contextClassLoader
    thread.contextClassLoader = loader
    try {
      val group = loader.loadClass("io.outfoxx.sunday.validation.$namespace.ModelMode$$mode")
      val interpolator =
        loader
          .loadClass("org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator")
          .getConstructor()
          .newInstance()
      if (namespace == "javax") {
        javax.validation.Validation
          .byDefaultProvider()
          .configure()
          .messageInterpolator(interpolator as javax.validation.MessageInterpolator)
          .buildValidatorFactory()
          .use { factory ->
            factory.validator
              .validate(value, group)
              .map { it.propertyPath.toString() }
              .toSet()
          }
      } else {
        jakarta.validation.Validation
          .byDefaultProvider()
          .configure()
          .messageInterpolator(interpolator as jakarta.validation.MessageInterpolator)
          .buildValidatorFactory()
          .use { factory ->
            factory.validator
              .validate(value, group)
              .map { it.propertyPath.toString() }
              .toSet()
          }
      }
    } finally {
      thread.contextClassLoader = previous
    }
  }
}

/** Binds the selected native provider before generated constructor adapters execute. */
internal fun <T> withNativeBeanValidation(
  namespace: String,
  parent: ClassLoader,
  block: () -> T,
): T =
  synchronized(NativeProviderLock) {
    val classpath = requireNotNull(System.getProperty("sunday.validation.$namespace.classpath"))
    val urls = classpath.split(File.pathSeparator).map { File(it).toURI().toURL() }.toTypedArray()
    URLClassLoader(urls, parent).use { loader ->
      val thread = Thread.currentThread()
      val previous = thread.contextClassLoader
      thread.contextClassLoader = loader
      try {
        val interpolator =
          loader
            .loadClass("org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator")
            .getConstructor()
            .newInstance()
        if (namespace == "javax") {
          javax.validation.Validation
            .byDefaultProvider()
            .configure()
            .messageInterpolator(interpolator as javax.validation.MessageInterpolator)
            .buildValidatorFactory()
            .use { factory -> bindNativeValidator(namespace, loader, factory.validator, block) }
        } else {
          jakarta.validation.Validation
            .byDefaultProvider()
            .configure()
            .messageInterpolator(interpolator as jakarta.validation.MessageInterpolator)
            .buildValidatorFactory()
            .use { factory -> bindNativeValidator(namespace, loader, factory.validator, block) }
        }
      } finally {
        thread.contextClassLoader = previous
      }
    }
  }

private fun <T> bindNativeValidator(
  namespace: String,
  loader: ClassLoader,
  validator: Any,
  block: () -> T,
): T {
  val type = loader.loadClass("io.outfoxx.sunday.validation.$namespace.ModelValidation")
  val instance = type.getField("INSTANCE").get(null)
  val previous = type.getMethod("getValidatorProvider").invoke(instance)
  val setter = type.methods.single { it.name == "setValidatorProvider" }
  val provider: () -> Any = { validator }
  setter.invoke(instance, provider)
  return try {
    block()
  } finally {
    setter.invoke(instance, previous)
  }
}

private object NativeProviderLock
