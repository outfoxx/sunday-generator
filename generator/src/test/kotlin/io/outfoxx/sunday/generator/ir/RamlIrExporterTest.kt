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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempFile

@ExtendWith(ResourceExtension::class)
class RamlIrExporterTest {

  @Test
  fun `exports RAML source to generated API IR`(
    @ResourceUri("raml/ir/craft-project.raml") sourceUri: URI,
  ) {

    val api = RamlIrExporter().export(sourceUri)

    assertThat(api.name, equalTo("Projects API"))
    assertThat(
      api.services
        .single()
        .operations
        .single()
        .id,
      equalTo("getProject"),
    )
  }

  @Test
  fun `exports RAML source to YAML`(
    @ResourceUri("raml/ir/craft-project.raml") sourceUri: URI,
  ) {

    val yaml = RamlIrExporter().exportYaml(sourceUri).normalizeLocation(sourceUri)

    assertEquals(expectedYaml("craft-project.ir.yaml"), yaml)
  }

  @Test
  fun `writes RAML source IR YAML to file`(
    @ResourceUri("raml/ir/craft-project.raml") sourceUri: URI,
  ) {

    val output = createTempFile("sunday-ir-export", ".yaml")

    RamlIrExporter().writeYaml(sourceUri, output)

    val yaml = Files.readString(output).normalizeLocation(sourceUri)
    assertEquals(expectedYaml("craft-project.ir.yaml"), yaml)
  }

  private fun String.normalizeLocation(sourceUri: URI): String =
    replace(sourceUri.toString(), "SOURCE_LOCATION")
      .replace("file://${sourceUri.path}", "SOURCE_LOCATION")
      .replace(sourceUri.path, "SOURCE_LOCATION")

  private fun expectedYaml(name: String): String =
    Files.readString(
      Path.of("src", "test", "resources", "ir", "expected", "RamlToGeneratedApiTest", name),
    )
}
