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

package io.outfoxx.sunday.generator.swift

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.Option.AddGeneratedHeader
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.SwiftCompilerExtension
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift] [RAML] Generated Annotations Test")
class RamlGeneratedAnnotationsTest {

  @Test
  fun `test generated annotation is added to root classes`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI,
  ) {
    val fs = Jimfs.newFileSystem(Configuration.unix())

    val typeRegistry = SwiftTypeRegistry(setOf(AddGeneratedHeader))

    generateTypes(testUri, typeRegistry, compiler)

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), fs.getPath("/"))

    val fileContents = Files.readString(fs.getPath("/Test.swift"))

    assertThat(fileContents, containsString("Generator: Sunday"))
    assertThat(fileContents, containsString("Test.swift"))
    assertThat(fileContents, containsString(LocalDate.now().format(DateTimeFormatter.ISO_DATE)))
  }

  @Test
  fun `test generated annotation is added to service class`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI,
  ) {
    val fs = Jimfs.newFileSystem(Configuration.unix())

    val typeRegistry = SwiftTypeRegistry(setOf())

    generateTypes(testUri, typeRegistry, compiler)

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), fs.getPath("/"))

    val fileContents = Files.readString(fs.getPath("/Test.swift"))

    assertThat(fileContents, not(containsString("Generator: Sunday")))
    assertThat(fileContents, not(containsString("Test.swift")))
    assertThat(fileContents, not(containsString(LocalDate.now().format(DateTimeFormatter.ISO_DATE))))
  }
}
