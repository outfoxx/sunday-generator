package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.tools.PlacementFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftPlacementTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `consumer uses binary owner without owner sources`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val original = PlacementFixture(directory, kind).api
    val owner = directory.resolve(if (kind == "raml") "common.raml" else "common.yaml").toUri()
    val consumer = directory.resolve(if (kind == "raml") "api.raml" else "api.yaml").toUri()
    val config =
      GeneratedPlacementConfiguration(
        defaults = GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SundayGenTest")),
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              owner.toString(),
              placement = GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SharedModels")),
            ),
          ),
      )
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      original.copy(placementConfiguration = config),
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val manifestPath = compiler.workDir.resolve("Package.swift")
    val manifest = Files.readString(manifestPath)
    val artifacts = compiler.workDir.resolve("owner-artifact")
    artifacts.toFile().deleteRecursively()
    Files.createDirectories(artifacts)
    try {
      Files.writeString(
        manifestPath,
        manifest
          .replace("SundayGenTest", "SharedModels")
          .replace(".library(name: \"SharedModels\",", ".library(name: \"SharedModels\", type: .dynamic,")
          .replace("path: \"src\"", "path: \"src/SharedModels\""),
      )
      registry.generateFiles(GeneratedOutputSelection(ownerUris = setOf(owner)), compiler.srcDir)
      assertTrue(compileGeneratedFiles(compiler))
      val buildRoot =
        compiler.workDir.resolve(
          if (compiler is io.outfoxx.sunday.generator.swift.tools.DockerSwiftCompiler) ".build" else "build",
        )
      val candidates =
        Files.walk(buildRoot).use { paths ->
          paths
            .filter {
              it.fileName.toString() in
                setOf(
                  "SharedModels.swiftmodule",
                  "SharedModels.swiftdoc",
                  "libSharedModels.dylib",
                  "libSharedModels.so",
                )
            }.toList()
        }
      for (artifact in candidates) {
        val destination = artifacts.resolve(artifact.fileName)
        if (Files.isDirectory(artifact)) {
          artifact.toFile().copyRecursively(destination.toFile(), overwrite = true)
        } else {
          Files.copy(artifact, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
      }
      assertTrue(Files.exists(artifacts.resolve("SharedModels.swiftmodule")))
      // SwiftPM 6.2 forwards only -I-prefixed arguments to synthesized test targets.
      val settings =
        """
        swiftSettings: [.unsafeFlags(["-I" + ownerArtifact])],
        linkerSettings: [.unsafeFlags(["-L", ownerArtifact, "-lSharedModels", "-Xlinker", "-rpath", "-Xlinker", ownerArtifact])]
        """.trimIndent()
      val consumerManifest =
        manifest
          .replace(
            "import PackageDescription",
            """
            import PackageDescription
            import Foundation
            let ownerArtifact = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
                .appendingPathComponent("owner-artifact").path
            """.trimIndent(),
          ).replace("path: \"src\"", "path: \"src/SundayGenTest\", $settings")
          .replace("path: \"tests\"", "path: \"tests\", $settings")
      Files.writeString(manifestPath, consumerManifest)
      registry.generateFiles(GeneratedOutputSelection(ownerUris = setOf(consumer)), compiler.srcDir)
      Files.createDirectories(compiler.testsDir)
      Files.writeString(
        compiler.testsDir.resolve("PlacementTests.swift"),
        """
        import Foundation
        import XCTest
        import SharedModels
        @testable import SundayGenTest
        final class PlacementTests: XCTestCase {
          func testSharedIdentity() throws {
            let data = Data(#"{"shared":"one"}"#.utf8)
            let value = try JSONDecoder().decode(Item.self, from: data)
            let shared: Shared = try XCTUnwrap(value.shared)
            XCTAssertEqual(shared.rawValue, "one")
            let encoded = try JSONEncoder().encode(value)
            let roundTrip = try JSONDecoder().decode(Item.self, from: encoded)
            XCTAssertEqual(roundTrip.shared, shared)
          }
        }
        """.trimIndent(),
      )
      assertTrue(
        io.outfoxx.sunday.generator.swift.tools
          .compileAndTestGeneratedFiles(compiler),
      )
    } finally {
      Files.writeString(manifestPath, manifest)
      artifacts.toFile().deleteRecursively()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `cross module references compile`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val input = PlacementFixture(directory, kind).api
    val service = input.services.first()
    val scope =
      io.outfoxx.sunday.generator.ir.GeneratedModelScope(
        service.name,
        service.operations.first().id,
        usage = io.outfoxx.sunday.generator.ir.GeneratedModelScope.Usage.RESPONSE_BODY,
      )
    val original =
      input.copy(
        models =
          input.models.map { model ->
            if (model.name == "Item") {
              model.copy(
                scope = scope,
                placementScopes =
                  model.placementScopes +
                    io.outfoxx.sunday.generator.ir.GeneratedPlacementScope(
                      model.declarationUri!!,
                      GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SharedModels")),
                    ),
              )
            } else {
              model
            }
          },
        services =
          input.services.map { candidate ->
            candidate.copy(
              operations =
                candidate.operations.map { operation ->
                  operation.copy(
                    responses =
                      operation.responses.map { response ->
                        response.copy(
                          payloads =
                            response.payloads.map { payload ->
                              payload.copy(
                                type =
                                  payload.type.let {
                                    if (it.name ==
                                      "Item"
                                    ) {
                                      it.copy(scope = scope)
                                    } else {
                                      it
                                    }
                                  },
                              )
                            },
                          type =
                            response.type?.let {
                              if (it.name ==
                                "Item"
                              ) {
                                it.copy(scope = scope)
                              } else {
                                it
                              }
                            },
                        )
                      },
                  )
                },
            )
          },
      )
    val owner = directory.resolve(if (kind == "raml") "common.raml" else "common.yaml").toUri().toString()
    val config =
      GeneratedPlacementConfiguration(
        defaults = GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SundayGenTest")),
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              owner,
              placement = GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SharedModels")),
            ),
          ),
      )
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      original.copy(placementConfiguration = config),
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    val manifestPath = compiler.workDir.resolve("Package.swift")
    val manifest = Files.readString(manifestPath)
    val altered =
      manifest
        .replace(
          "targets: [\n        .target(",
          """
          targets: [
            .target(name: "SharedModels",
              dependencies: [.product(name: "Sunday", package: "sunday-swift")], path: "src/SharedModels"),
            .target(
          """.trimIndent(),
        ).replace(
          "dependencies: [\n            .product",
          "dependencies: [\n            \"SharedModels\",\n            .product",
        ).replace("path: \"src\"", "path: \"src/SundayGenTest\"")
    try {
      Files.writeString(manifestPath, altered)
      assertTrue(compileGeneratedFiles(compiler))
      assertTrue(
        registry.buildTypes().keys.any {
          it.simpleName == "Item" &&
            it.moduleName == "SharedModels" &&
            it.topLevelTypeName() == it
        },
      )
    } finally {
      Files.writeString(manifestPath, manifest)
    }
  }
}
