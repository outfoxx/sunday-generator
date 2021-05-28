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

package io.outfoxx.sunday.generator.typescript

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.TypeScriptCompilerExtension
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_DATE

@ExtendWith(ResourceExtension::class, TypeScriptCompilerExtension::class)
@DisplayName("[TypeScript] [RAML] Generated Annotations Test")
class RamlGeneratedAnnotationsTest {

  @Test
  fun `test generated annotation is added to files`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {
    val fs = Jimfs.newFileSystem(Configuration.unix())

    val typeRegistry = TypeScriptTypeRegistry(setOf(AddGenerationHeader))

    generateTypes(testUri, typeRegistry, compiler)

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), fs.getPath("/"))

    val fileContents = Files.readString(fs.getPath("/test.ts"))

    assertThat(fileContents, containsString("Generator: Sunday"))
    assertThat(fileContents, containsString("test.ts"))
    assertThat(fileContents, containsString(LocalDate.now().format(ISO_DATE)))
  }

  @Test
  fun `test no generated annotation is added to files`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {
    val fs = Jimfs.newFileSystem(Configuration.unix())

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    generateTypes(testUri, typeRegistry, compiler)

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), fs.getPath("/"))

    val fileContents = Files.readString(fs.getPath("/test.ts"))

    assertThat(fileContents, not(containsString("Generator: Sunday")))
    assertThat(fileContents, not(containsString("test.ts")))
    assertThat(fileContents, not(containsString(LocalDate.now().format(ISO_DATE))))
  }
}
