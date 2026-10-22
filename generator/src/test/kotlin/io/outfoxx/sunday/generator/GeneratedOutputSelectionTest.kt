package io.outfoxx.sunday.generator

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
}
