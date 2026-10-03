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

package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.AssociatedExtensions
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.tag
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] ObjectModels Test")
@Tag("models")
class SwiftSundayIrObjectModelsTest : SwiftSundayIrTestSupport() {

  @Test
  @Tag("responses")
  fun `qualifies Sunday Problem when generated model has same name`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Narrative API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Problem",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "code",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                  GeneratedModelProperty(
                    "detail",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = false,
                  ),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", nullable = true, format = "uri"),
                    required = false,
                  ),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "NarrativeProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Problem")),
              properties =
                listOf(
                  GeneratedModelProperty("narrativeId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val problemSource =
      buildString {
        FileSpec
          .get("", findType("Problem", builtTypes))
          .writeTo(this)
      }
    val narrativeProblemSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(problemSource.contains("public protocol Problem : Sunday.Problem"), problemSource)
    assertTrue(narrativeProblemSource.contains("public struct NarrativeProblem : Problem"), narrativeProblemSource)
  }

  @Test
  fun `generates nested shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Nested API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Container",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("child", GeneratedTypeRef.named("Child"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Child",
              kind = GeneratedModel.Kind.OBJECT,
              nested =
                GeneratedNestedType(
                  enclosedIn = GeneratedTypeRef.named("Container"),
                  name = "Child",
                ),
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("Container", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let child: Child"), source)
    assertTrue(source.contains("public struct Child"), source)
    assertTrue(source.contains("public let value: String"), source)
  }

  @Test
  fun `resolves duplicate imported model names by source identity from IR`(compiler: SwiftCompiler) {
    val librarySource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "libraries/common.raml")
    val mainSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml")
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Imported API",
        source = mainSource,
        models =
          listOf(
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = mainSource,
              targets = mapOf("swift" to GeneratedTarget(typeName = "MainTest")),
              properties =
                listOf(
                  GeneratedModelProperty("mainValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              targets = mapOf("swift" to GeneratedTarget(typeName = "LibraryTest")),
              properties =
                listOf(
                  GeneratedModelProperty("libraryValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Consumer",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("main", GeneratedTypeRef.named("Test", source = mainSource), required = true),
                  GeneratedModelProperty(
                    "library",
                    GeneratedTypeRef.named("Test", source = librarySource),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("Consumer", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let main: MainTest"), source)
    assertTrue(source.contains("public let library: LibraryTest"), source)
  }

  @Test
  @Tag("requests")
  fun `generates patchable shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Patch API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "PatchModel",
              kind = GeneratedModel.Kind.OBJECT,
              patchable = true,
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "nullable",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("PatchModel", builtTypes)
    val source =
      buildString {
        FileSpec
          .builder("", typeSpec.name)
          .addType(typeSpec)
          .apply {
            typeSpec.tag<AssociatedExtensions>()?.forEach { addExtension(it) }
          }.build()
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public struct PatchModel"), source)
    assertTrue(source.contains("Sendable"), source)
    assertTrue(source.contains("public var value: UpdateOp<String>"), source)
    assertTrue(source.contains("public var nullable: UpdateOp<String>"), source)
    assertTrue(source.contains("value: UpdateOp<String> = .unchanged"), source)
    assertTrue(source.contains("nullable: UpdateOp<String> = .unchanged"), source)
    assertTrue(source.contains("self.value = try container.decode(UpdateOp<String>.self, forKey: .value)"), source)
    assertTrue(source.contains("try container.encode(self.value, forKey: .value)"), source)
    assertTrue(source.contains("extension AnyPatchOp where Value == PatchModel"), source)
  }

  @Test
  fun `Swift Sunday IR renderer does not read AMF service model types`() {
    val source =
      Files.readString(
        Path.of(
          "src",
          "main",
          "kotlin",
          "io",
          "outfoxx",
          "sunday",
          "generator",
          "swift",
          "SwiftSundayIrGenerator.kt",
        ),
      )

    assertFalse(source.contains("amf."), source)
    assertFalse(source.contains("processService"), source)
    assertFalse(source.contains("processResourceMethod"), source)
    assertFalse(source.contains("processReturnType("), source)
  }

  @Test
  fun `Swift Sunday preserves OpenAPI inline object properties beside conditional allOf`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/inline-object-conditional-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))

    val dataSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/VisualizationRenderGraphTaskSettledData.swift",
      )
    val renderGraphSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/VisualizationRenderGraphTaskSettledDataRenderGraph.swift",
      )
    val allOfRequiredSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/AllOfRequiredData.swift",
      )
    assertTrue(
      dataSource.contains(
        "public let renderGraph: VisualizationRenderGraphTaskSettledDataRenderGraph",
      ),
      dataSource,
    )
    assertTrue(allOfRequiredSource.contains("public let value: String"), allOfRequiredSource)
    assertTrue(renderGraphSource.contains("public let graphJobId: String"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let taskId: String"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let state: RenderGraphTaskSettledState"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let completedCount: Int"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let taskCount: Int"), renderGraphSource)
  }

  @Test
  fun `generates shared object models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Model API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty(
                    "metadata",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "array",
                      arguments = listOf(GeneratedTypeRef.scalar("object")),
                    ),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("User", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let id: String"), source)
    assertTrue(source.contains("public let displayName: String?"), source)
    assertTrue(source.contains("public let metadata: [[String : AnyValue]]"), source)
    assertTrue(source.contains("case displayName = \"displayName\""), source)
  }

  @Test
  fun `adds Identifiable to IR models with camel or acronym id suffixes`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))
    val api =
      GeneratedApi(
        name = "Identifiable API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "UserRef",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "TeamRef",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("teamID", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "NotIdentifiable",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("userid", GeneratedTypeRef.scalar("string"), required = true)),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val userRefSource =
      buildString {
        FileSpec
          .get("", findType("UserRef", builtTypes))
          .writeTo(this)
      }
    val teamRefSource =
      buildString {
        FileSpec
          .get("", findType("TeamRef", builtTypes))
          .writeTo(this)
      }
    val notIdentifiableSource =
      buildString {
        FileSpec
          .get("", findType("NotIdentifiable", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      userRefSource.contains("public struct UserRef : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      userRefSource,
    )
    assertTrue(userRefSource.contains("public var id: String"), userRefSource)
    assertTrue(userRefSource.contains("return self.userId"), userRefSource)
    assertTrue(
      teamRefSource.contains("public struct TeamRef : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      teamRefSource,
    )
    assertTrue(teamRefSource.contains("return self.teamID"), teamRefSource)
    assertFalse(
      notIdentifiableSource.contains(
        "public struct NotIdentifiable : Codable, CustomDebugStringConvertible, Sendable, Identifiable",
      ),
      notIdentifiableSource,
    )
  }
}
