package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.tools.ResponseUnionOutputFixture
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftOperationModelOutputTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `services use hoisted payloads from binary model artifact`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api = ResponseUnionOutputFixture(directory, kind, unionBody = false).api
    val payloadName =
      api.models
        .single {
          it.properties.any { property ->
            property.name == "value"
          }
        }.name
        .toUpperCamelCase()
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      api,
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
      registry.generateFiles(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model)), compiler.srcDir)
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
      registry.generateFiles(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service)), compiler.srcDir)
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
            let data = Data(#"{"value":"example"}"#.utf8)
            let value = try JSONDecoder().decode($payloadName.self, from: data)
            XCTAssertEqual(value.value, "example")
            let encoded = try JSONEncoder().encode(value)
            let roundTrip = try JSONDecoder().decode($payloadName.self, from: encoded)
            XCTAssertEqual(roundTrip.value, value.value)
          }
        }
        """.trimIndent(),
      )
      assertTrue(
        io.outfoxx.sunday.generator.swift.tools
          .compileAndTestGeneratedFiles(compiler),
      )
      assertFalse(
        registry
          .buildTypes(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service)))
          .keys
          .any { it.moduleName == "SharedModels" },
      )
    } finally {
      Files.writeString(manifestPath, manifest)
      artifacts.toFile().deleteRecursively()
    }
  }
}
