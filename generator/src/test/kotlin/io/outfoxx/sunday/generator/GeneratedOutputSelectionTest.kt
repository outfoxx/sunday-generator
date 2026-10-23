package io.outfoxx.sunday.generator

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class GeneratedOutputSelectionTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `configuration filenames and file URIs share canonical identity`() {
    val file = Files.writeString(directory.resolve("schema with spaces.yaml"), "{}")
    val link = Files.createSymbolicLink(directory.resolve("alias.yaml"), file)
    val expected = file.toFile().canonicalFile.toURI()
    assertEquals(expected, GeneratedDocumentUri.configuration("schema with spaces.yaml", directory))
    assertEquals(expected, GeneratedDocumentUri.configuration(link.toString(), directory))
    assertEquals(expected, GeneratedDocumentUri.canonical(file.toUri()))
    assertThrows(IllegalArgumentException::class.java) { GeneratedDocumentUri.canonical(URI("relative.yaml")) }
  }

  @Test
  fun `category and owner filters are independent and broadest first`() {
    val first = URI("https://example.test/a.yaml?version=1")
    val second = URI("https://example.test/b.yaml")
    val all = GeneratedOutputSelection()
    GeneratedTypeCategory.entries.forEach { assertTrue(all.includes(it, emptySet())) }
    val models = GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model), setOf(first))
    assertFalse(models.includes(GeneratedTypeCategory.Service, emptySet()))
    assertTrue(models.includes(GeneratedTypeCategory.Model, setOf(first)))
    assertFalse(models.includes(GeneratedTypeCategory.Model, setOf(second)))
    assertThrows(IllegalArgumentException::class.java) { models.includes(GeneratedTypeCategory.Model, emptySet()) }
    val owners = GeneratedOutputSelection(ownerUris = setOf(first))
    assertTrue(owners.includes(GeneratedTypeCategory.Service, setOf(second, first)))
    assertThrows(IllegalArgumentException::class.java) {
      owners.includes(GeneratedTypeCategory.Model, setOf(second, first))
    }
    assertEquals(
      URI("https://example.test/a//b"),
      GeneratedDocumentUri.canonical(URI("https://example.test/a//b#schema")),
    )
    assertFalse(owners.includes(GeneratedTypeCategory.Model, setOf(URI("https://example.test/a.yaml?version=2"))))
    assertEquals(first, GeneratedDocumentUri.configuration("https://example.test/./a.yaml?version=1#/A", directory))
  }

  @Test
  fun `hoisted models preserve composed service ownership independently of category`() {
    val first = URI("https://example.test/first.yaml")
    val second = URI("https://example.test/second.yaml")
    val service = GeneratedOutputOrigin(GeneratedTypeCategory.Service, setOf(first, second))
    val model = service.copy(category = GeneratedTypeCategory.Model)
    for (owner in setOf(first, second)) {
      assertTrue(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model), setOf(owner)).includes(model))
      assertFalse(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service), setOf(owner)).includes(model))
      assertFalse(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model), setOf(owner)).includes(service))
    }
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedOutputSelection(ownerUris = setOf(first)).includes(model.copy(serviceOwned = false))
    }
  }

  @Test
  fun `lexical ownership keeps distinct document query identities`() {
    val document = "https://example.test/api.yaml?version=1"
    val operation = "$document#/paths/~1items/get"
    val api =
      GeneratedApi(
        name = "Versions",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, document),
        services =
          listOf(
            GeneratedService(
              "Items",
              operations =
                listOf(
                  GeneratedOperation("getItems", "GET", "/items", declarationUri = operation),
                ),
            ),
          ),
      )
    val local =
      GeneratedModel(
        "Local",
        GeneratedModel.Kind.OBJECT,
        declarationUri = "$operation/responses/200/schema",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
      )
    assertEquals(GeneratedTypeCategory.Service, local.outputOrigin(api).category)
    val external = local.copy(declarationUri = local.declarationUri!!.replace("version=1", "version=2"))
    assertEquals(GeneratedTypeCategory.Model, external.outputOrigin(api).category)
  }

  @Test
  fun `every enclosing declaration must be selected`() {
    val a = URI("https://example.test/a.yaml")
    val b = URI("https://example.test/b.yaml")
    val c = URI("https://example.test/c.yaml")
    val root = GeneratedOutputOrigin(GeneratedTypeCategory.Model, setOf(a))
    val middle = GeneratedOutputOrigin(GeneratedTypeCategory.Model, setOf(b), enclosing = root)
    val leaf = GeneratedOutputOrigin(GeneratedTypeCategory.Model, setOf(c), enclosing = middle)
    assertTrue(GeneratedOutputSelection().includes(leaf))
    assertFalse(GeneratedOutputSelection(ownerUris = setOf(a, c)).includes(leaf))
    assertFalse(GeneratedOutputSelection(ownerUris = setOf(b, c)).includes(leaf))
    assertTrue(GeneratedOutputSelection(ownerUris = setOf(a, b, c)).includes(leaf))
    assertFalse(
      GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model)).includes(
        leaf.copy(enclosing = middle.copy(category = GeneratedTypeCategory.Service)),
      ),
    )
  }

  @Test
  fun `ancestor requirements retain backend problem classification`() {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml")
    val parent = GeneratedModel("Parent", GeneratedModel.Kind.OBJECT, source)
    val child =
      GeneratedModel(
        "Child",
        GeneratedModel.Kind.OBJECT,
        source,
        nested =
          io.outfoxx.sunday.generator.ir.GeneratedNestedType(
            enclosedIn =
              io.outfoxx.sunday.generator.ir.GeneratedTypeRef
                .named("Parent"),
            name = "Child",
          ),
      )
    val api = GeneratedApi(name = "Nested", source = source, models = listOf(parent, child))
    val origin = child.outputOrigin(api) { _, _ -> GeneratedTypeCategory.Problem }
    assertEquals(GeneratedTypeCategory.Problem, origin.enclosing!!.category)
    assertTrue(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Problem)).includes(origin))
    assertFalse(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model)).includes(origin))
  }
}
