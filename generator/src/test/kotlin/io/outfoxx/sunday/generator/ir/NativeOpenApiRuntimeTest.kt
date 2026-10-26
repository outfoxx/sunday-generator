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

package io.outfoxx.sunday.generator.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.writeText

/** Exercises the public exporter without AMF or dependencies supplied by the test harness. */
class NativeOpenApiRuntimeTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `native OpenAPI consumer resolves problem URI templates with AMF excluded`() {
    val source = directory.resolve("problems.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Problems, version: '1'}
      servers: [{url: 'https://api.example.com/v1/'}]
      x-sunday-problemBaseUri: '{base}/'
      x-sunday-problemUriParams: {base: problems}
      x-sunday-problemTypes:
        missing: {status: 404, title: Missing}
      paths: {}
      components:
        schemas:
          MissingProblem:
            type: object
            required: [type, title, status]
            properties:
              type: {type: string, default: missing}
              title: {type: string, const: Missing}
              status: {type: integer, const: 404}
      """.trimIndent(),
    )
    val classpath =
      requireNotNull(System.getProperty("sunday.validation.native-openapi.classpath"))
        .split(File.pathSeparator)
        .map { File(it).toURI().toURL() }
    // The platform parent prevents AMF and generated-code test dependencies from masking missing library dependencies.
    URLClassLoader(classpath.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
      assertNull(loader.getResource("amf/apicontract/client/platform/AMFConfiguration.class"))
      assertNull(loader.getResource("org/junit/jupiter/api/Test.class"))
      val exporterClass = loader.loadClass(GeneratedApiIrExporter::class.java.name)
      val kindClass = loader.loadClass(GeneratedApiIrSourceKind::class.java.name)
      val kind = kindClass.enumConstants.single { it.toString() == "AUTO" }
      val yaml =
        exporterClass
          .getMethod("exportYaml", List::class.java, kindClass)
          .invoke(exporterClass.getConstructor().newInstance(), listOf(source.toUri()), kind) as String
      val api = GeneratedApiYaml.readString(yaml)
      val expected = "https://api.example.com/v1/problems/missing"
      assertEquals(expected, api.problems.single().typeUri)
      val type =
        api.models
          .single()
          .properties
          .single { it.name == "type" }
      assertEquals(expected, type.defaultValue)
    }
  }
}
