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

package io.outfoxx.sunday.generator.tools

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader

/** Keeps generated Kotlin compilation independent of the generator and test harness libraries. */
@Tag("kotlin")
class CompilerClasspathTest {

  @Test
  fun `explicit classpath retains runtime APIs and fixtures without compiler or parser infrastructure`() {
    val files =
      requireNotNull(System.getProperty("sunday.validation.kotlin.classpath"))
        .split(File.pathSeparator)
        .map(::File)
        .filter(File::exists)
    URLClassLoader(files.map { it.toURI().toURL() }.toTypedArray(), null).use { loader ->
      listOf(
        "kotlin/Unit.class",
        "javax/ws/rs/GET.class",
        "jakarta/ws/rs/GET.class",
        "javax/validation/Valid.class",
        "jakarta/validation/Valid.class",
        "com/fasterxml/jackson/databind/ObjectMapper.class",
        "io/test/client/GraphsClientFilter.class",
        "org/eclipse/microprofile/rest/client/annotation/ClientHeaderParam.class",
      ).forEach { name -> assertNotNull(loader.getResource(name), name) }
      listOf(
        "org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class",
        "com/tschuchort/compiletesting/KotlinCompilation.class",
        "amf/apicontract/client/platform/AMFConfiguration.class",
        "org/junit/jupiter/api/Test.class",
      ).forEach { name -> assertNull(loader.getResource(name), name) }
    }
  }
}
