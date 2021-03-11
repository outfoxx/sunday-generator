package io.outfoxx.sunday.generator

import java.nio.file.Path

interface TypeRegistry {

  fun generateFiles(categories: Set<GeneratedTypeCategory>, outputDirectory: Path)

}
