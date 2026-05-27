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
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.TypeSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.exists

class SwiftTypeRegistryTest {

  @Test
  fun `generate files removes legacy generated files that collide with the current layout`() {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val outputDirectory = fs.getPath("/")
    val legacyFile = outputDirectory.resolve("Models/BranchNotFoundProblem.swift")
    val userFile = outputDirectory.resolve("Models/UserResponse.swift")

    Files.createDirectories(legacyFile.parent)
    Files.writeString(legacyFile, "/// Generator: Sunday\npublic class BranchNotFoundProblem {}\n")
    Files.writeString(userFile, "public struct UserResponse {}\n")

    val typeRegistry = SwiftTypeRegistry(setOf(AddGeneratedHeader))
    typeRegistry.addModelType(
      DeclaredTypeName("TurnPost", "BranchNotFoundProblem"),
      TypeSpec.classBuilder("BranchNotFoundProblem"),
      outputGroup = "Repos",
    )
    typeRegistry.addModelType(
      DeclaredTypeName("TurnPost", "UserResponse"),
      TypeSpec.enumBuilder("UserResponse"),
      outputDirectory = SwiftTypeRegistry.OutputDirectory.Responses,
      outputGroup = "Users",
    )

    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), outputDirectory)

    assertThat(legacyFile.exists(), not(true))
    assertThat(userFile.exists(), not(false))
    assertThat(outputDirectory.resolve("Repos/Models/BranchNotFoundProblem.swift").exists(), not(false))
    assertThat(outputDirectory.resolve("Users/Responses/UserResponse.swift").exists(), not(false))
  }

  @Test
  fun `generate files records and removes stale manifest entries`() {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val outputDirectory = fs.getPath("/")

    val oldTypeRegistry =
      SwiftTypeRegistry(setOf(AddGeneratedHeader)).apply {
        addModelType(
          DeclaredTypeName("TurnPost", "OldModel"),
          TypeSpec.classBuilder("OldModel"),
        )
      }
    oldTypeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), outputDirectory)

    val newTypeRegistry =
      SwiftTypeRegistry(setOf(AddGeneratedHeader)).apply {
        addModelType(
          DeclaredTypeName("TurnPost", "NewModel"),
          TypeSpec.classBuilder("NewModel"),
        )
      }
    newTypeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), outputDirectory)

    assertThat(outputDirectory.resolve("Models/OldModel.swift").exists(), not(true))
    assertThat(outputDirectory.resolve("Models/NewModel.swift").exists(), not(false))
    assertThat(
      Files.readString(outputDirectory.resolve(".sunday-swift-generated-files")),
      containsString("Models/NewModel.swift"),
    )
  }
}
