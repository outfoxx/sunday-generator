/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.tools

import java.nio.file.Path

/**
 * Tracks generated source that has passed a language compiler in the current test.
 *
 * Generated-code snapshots must only be produced from sources recorded here. This
 * keeps snapshot assertions from drifting back to raw `FileSpec`/`ModuleSpec`
 * rendering that has not been proven to compile.
 */
object CompiledGeneratedSources {

  private val compiledSources = ThreadLocal.withInitial { linkedSetOf<CompiledSourceKey>() }

  /** Clears the current test thread's compiled source set before a new compile run. */
  fun beginCompile() {
    clear()
  }

  /** Clears the current test thread's compiled source set before a test starts. */
  fun clear() {
    compiledSources.get().clear()
  }

  /** Records a generated source file after its compile run succeeds. */
  fun record(
    language: GeneratedCodeLanguage,
    path: String,
    source: String,
  ) {
    compiledSources.get().add(CompiledSourceKey(language, path, source.normalizedGeneratedSource()))
  }

  /** Records generated source files under a source root after their compile run succeeds. */
  fun recordAll(
    language: GeneratedCodeLanguage,
    sourceRoot: Path,
    files: Map<Path, String>,
  ) {
    files.forEach { (path, source) ->
      record(language, sourceRoot.relativize(path).toString(), source)
    }
  }

  /** Fails if a generated-code snapshot attempts to use source that has not compiled. */
  fun requireCompiled(
    language: GeneratedCodeLanguage,
    source: String,
  ) {
    val normalizedSource = source.normalizedGeneratedSource()
    if (compiledSources.get().none { it.language == language && it.source == normalizedSource }) {
      throw AssertionError(
        "Generated $language snapshot source was not produced by a successful compile in this test. " +
          "Generate, compile, and read the source from the compiled output before snapshotting it.",
      )
    }
  }

  /** Returns a compiled generated source file by path. */
  fun source(
    language: GeneratedCodeLanguage,
    path: String,
  ): String =
    compiledSources
      .get()
      .singleOrNull { it.language == language && it.path == path }
      ?.source
      ?: throw AssertionError("Compiled $language source file was not found: $path")

  private data class CompiledSourceKey(
    val language: GeneratedCodeLanguage,
    val path: String,
    val source: String,
  )
}

/** Languages with generated-code snapshot compile gates. */
enum class GeneratedCodeLanguage {
  Kotlin,
  Python,
  Swift,
  TypeScript,
}

internal fun String.normalizedGeneratedSource(): String = replace("\r\n", "\n")
