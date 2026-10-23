package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.SharedHelpersFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftSharedHelpersTest {
  @ParameterizedTest
  @CsvSource(
    "service,ModelCodingSupport",
    "aggregate,ModelCodingSupport",
    "problem,ModelCodingSupport",
    "service,MergePatchSupport",
    "aggregate,MergePatchSupport",
    "problem,MergePatchSupport",
  )
  fun `shared helper names avoid other generated declarations`(
    kind: String,
    name: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val api = SharedHelpersFixture(directory, "openapi").withHelperCollision(kind, name)
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions(
        "https://test/",
        listOf("application/json"),
        "",
        aggregateServices = kind == "aggregate",
        aggregateServiceName = name,
      ),
    ).generateServiceTypes()
    registry.generateFiles(outputDirectory = compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("SharedHelperCollisionTests.swift"),
      """
      import Testing
      import SundayGenTest
      @Test func sharedHelperCollision() throws {
        let value = FirstUpdate(value: "before")
        #expect(try value.merge(FirstUpdatePatch(value: .delete)).value == nil)
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @ParameterizedTest
  @CsvSource(
    "raml,all",
    "openapi,all",
    "asyncapi,all",
    "composed,all",
    "raml,second",
    "openapi,first",
    "openapi,second",
    "asyncapi,second",
    "composed,second",
    "openapi,split",
    "openapi,collision",
    "openapi,neither",
  )
  fun `shared helpers compile with only selected consumers`(
    kind: String,
    scenario: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val fixture =
      SharedHelpersFixture(directory, kind, split = scenario == "split", collision = scenario == "collision")
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(fixture.api, registry, swiftSundayTestOptions).generateServiceTypes()
    registry.generateFiles(fixture.selection(scenario), compiler.srcDir)
    val paths =
      Files.walk(compiler.srcDir).use { files ->
        files.filter { it.toString().endsWith(".swift") }.toList().map { compiler.srcDir.relativize(it).toString() }
      }
    Files.writeString(compiler.srcDir.resolve("Consumer.swift"), "public struct Consumer {}")
    val models =
      listOf("FirstUpdate", "SecondUpdate").filter {
        scenario != "neither" &&
          (scenario != "first" || it == "FirstUpdate") &&
          (scenario != "second" || it == "SecondUpdate")
      }
    val check =
      models.joinToString("\n") { name ->
        val type = if (scenario == "split" && name == "SecondUpdate") "SharedModels.$name" else name
        """
        do {
          let value = $type(value: "before")
          let encoder = JSONEncoder()
          encoder.outputFormatting = .sortedKeys
          #expect(try encoder.encode(value.patch()) == encoder.encode(${type}Patch.fromModel(value)))
          #expect(try value.merge(${type}Patch()).value == "before")
          #expect(try value.merge($type(value: "after").patch(from: value)).value == "after")
          #expect(try value.merge(${type}Patch(value: .delete)).value == nil)
          #expect(value.value == "before")
          let data = Data(#"{"value":"before","extra":{"items":[{"nested":2}]}}"#.utf8)
          let dynamic = try JSONDecoder().decode($type.self, from: data)
          let roundTrip = try encoder.encode(dynamic)
          #expect(try JSONSerialization.jsonObject(with: roundTrip) as! NSDictionary == JSONSerialization.jsonObject(with: data) as! NSDictionary)
        }
        """.trimIndent()
      }
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("SharedHelpersTests.swift"),
      """
      import Foundation
      import Testing
      import SundayGenTest
      ${if (scenario == "split") "import SharedModels" else ""}
      @Test func sharedHelpers() throws {
      $check
      }
      """.trimIndent(),
    )
    val manifestPath = compiler.workDir.resolve("Package.swift")
    val manifest = Files.readString(manifestPath)
    if (scenario == "split") {
      Files.writeString(
        manifestPath,
        manifest
          .replace("path: \"src\"", "path: \"src\", exclude: [\"SharedModels\"]")
          .replace(
            "targets: [\n        .target(",
            "targets: [\n        .target(name: \"SharedModels\", dependencies: [.product(name: \"Sunday\", package: \"sunday-swift\")], path: \"src/SharedModels\"),\n        .target(",
          ).replace(
            "dependencies: [\n            .product",
            "dependencies: [\n            \"SharedModels\",\n            .product",
          ),
      )
    }
    try {
      assertTrue(compileAndTestGeneratedFiles(compiler))
      val sources = paths.map { CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, it) }
      assertEquals(
        when (scenario) {
          "neither" -> 0
          "split" -> 2
          else -> 1
        },
        sources.count { it.contains("Shared RFC 7396 operations") },
      )
      val count =
        when (scenario) {
          "neither" -> 0
          "split" -> 2
          else -> 1
        }
      assertEquals(count, sources.sumOf { Regex("struct AdditionalPropertyValue").findAll(it).count() })
      assertEquals(count, sources.sumOf { Regex("struct UnknownPropertyCodingKey").findAll(it).count() })
      sources.filter { it.contains("Shared RFC 7396 operations") }.forEach {
        assertEquals(1, Regex("enum MergePatchSupport").findAll(it).count())
      }
    } finally {
      Files.writeString(manifestPath, manifest)
    }
  }
}
