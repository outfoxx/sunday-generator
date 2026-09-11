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

@file:Suppress("UnstableApiUsage")

package io.outfoxx.sunday.generator.gradle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.outfoxx.sunday.generator.common.APIProcessor
import io.outfoxx.sunday.generator.ir.OpenApiDocumentLoader
import io.outfoxx.sunday.generator.ir.OpenApiDocumentSnapshot
import io.outfoxx.sunday.generator.ir.OpenApiLoadedDocument
import io.outfoxx.sunday.generator.ir.OpenApiReferenceOptions
import io.outfoxx.sunday.generator.ir.OpenApiReferenceResolver
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.location
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import javax.inject.Inject

@CacheableTask
abstract class SundayDiscoverIncludes
  @Inject
  constructor(
    objects: ObjectFactory,
    layout: ProjectLayout,
  ) : SourceTask() {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource(): FileTree = super.getSource()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    val bootstrapAllSources: Property<FileCollection> = objects.property(FileCollection::class.java)

    @OutputFile
    val rootsIndexFile: RegularFileProperty = objects.fileProperty()

    @OutputFile
    val allSourcesIndexFile: RegularFileProperty = objects.fileProperty()

    /** Captured content used by generation, independent of HTTP cache metadata. */
    @get:OutputDirectory
    val capturedDocumentsDirectory: DirectoryProperty =
      objects.directoryProperty().convention(
        layout.buildDirectory.dir("generated/sunday/documents/$name"),
      )

    /** Base used to encode local document paths in relocatable manifests. */
    @get:Internal
    val sourceBaseDirectory: DirectoryProperty = objects.directoryProperty().convention(layout.projectDirectory)

    /** Persistent HTTP cache, separate from the declared generation inputs. */
    @get:Internal
    val openApiReferenceCacheDirectory: DirectoryProperty =
      objects.directoryProperty().fileValue(
        OpenApiReferenceOptions().cacheDirectory.toFile(),
      )

    /** Whether HTTP retrieval is disabled for this invocation. */
    @get:Input
    val openApiOffline: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    init {
      outputs.upToDateWhen { !hasOpenApiSources() }
      outputs.doNotCacheIf("OpenAPI references must be revalidated for each invocation") { hasOpenApiSources() }
    }

    private val apiProcessor = APIProcessor()

    private fun hasOpenApiSources(): Boolean = source.files.any { it.isFile && it.isOpenApiSource() }

    private fun File.isOpenApiSource(): Boolean =
      extension.lowercase() in setOf("yaml", "yml", "json") &&
        runCatching { yamlMapper.readTree(this)?.has("openapi") == true }.getOrDefault(false)

    @TaskAction
    fun discover() {
      val roots =
        source.files
          .filter { file -> file.isFile && file.isRootSourceDocument() }
          .map { file -> file.canonicalFile }
          .toSortedSet(compareBy { file -> file.absolutePath })

      val allSources = roots.toMutableSet()
      val captured = linkedMapOf<URI, OpenApiLoadedDocument>()
      val loader =
        OpenApiDocumentLoader.create(
          OpenApiReferenceOptions(
            cacheDirectory = openApiReferenceCacheDirectory.get().asFile.toPath(),
            offline = openApiOffline.get(),
          ),
        )

      roots.forEach { file ->
        val discovered =
          when {
            file.isOpenApiSource() -> {
              val resolution = OpenApiReferenceResolver(loader).resolve(file.toURI())
              captured.putAll(resolution.documents)
              resolution.documents.keys
                .filter { it.scheme == "file" }
                .map { File(it).canonicalFile }
                .toSet()
            }
            file.extension.lowercase() == "raml" -> discoverRamlSources(file)
            else -> discoverNativeSources(file)
          }
        discovered.forEach { allSources.add(it) }
        captured.putIfAbsent(file.toURI(), OpenApiLoadedDocument(file.toURI(), file.readBytes()))
      }
      OpenApiDocumentSnapshot.write(
        capturedDocumentsDirectory.get().asFile.toPath(),
        sourceBaseDirectory.get().asFile.toPath(),
        captured,
      )

      writeIndex(rootsIndexFile.get().asFile, roots)
      writeIndex(allSourcesIndexFile.get().asFile, allSources)
    }

    private fun File.isRootSourceDocument(): Boolean =
      when (extension.lowercase()) {
        "raml" -> isRootRamlDocument()
        "yaml", "yml", "json" -> isNativeRootSourceDocument()
        else -> false
      }

    private fun File.isRootRamlDocument(): Boolean {
      val header = bufferedReader().use { it.readLine() }?.trim() ?: return false
      return header == "#%RAML 1.0" ||
        header.startsWith("#%RAML 1.0 Overlay") ||
        header.startsWith("#%RAML 1.0 Extension")
    }

    private fun File.isNativeRootSourceDocument(): Boolean =
      runCatching {
        val root = yamlMapper.readTree(this)
        root?.has("openapi") == true || root?.has("asyncapi") == true
      }.getOrDefault(false)

    private fun discoverRamlSources(file: File): Set<File> {
      val processed = apiProcessor.process(file.toURI())
      processed.validationLog.forEach {
        val level =
          when (it.level) {
            APIProcessor.Result.Level.Error -> LogLevel.ERROR
            APIProcessor.Result.Level.Warning -> LogLevel.WARN
            APIProcessor.Result.Level.Info -> LogLevel.INFO
          }
        logger.log(level, "${it.file}:${it.line}: ${it.message}")
      }

      return processed
        .document
        .allUnits
        .mapNotNull { unit -> unit.location }
        .mapNotNull { location ->
          when {
            location.startsWith("file:", ignoreCase = true) -> runCatching { File(URI(location)) }.getOrNull()
            location.isNotBlank() -> File(location)
            else -> null
          }
        }.filter { it.exists() }
        .map { it.canonicalFile }
        .toSet()
    }

    private fun discoverNativeSources(file: File): Set<File> {
      val discovered = linkedSetOf<File>()
      val visited = mutableSetOf<File>()
      val pending = ArrayDeque<File>()
      pending.add(file.canonicalFile)

      while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (!visited.add(current)) {
          continue
        }

        findNativeReferences(current).forEach { referenced ->
          if (discovered.add(referenced)) {
            pending.add(referenced)
          }
        }
      }

      return discovered
    }

    private fun findNativeReferences(file: File): Set<File> {
      val root = runCatching { yamlMapper.readTree(file) }.getOrNull() ?: return emptySet()
      val references = linkedSetOf<File>()

      root.visitReferences { reference ->
        resolveNativeReference(file, reference)?.let { references.add(it) }
      }

      return references
    }

    private fun JsonNode.visitReferences(visit: (String) -> Unit) {
      when {
        isObject -> {
          properties().forEach { (name, value) ->
            if (name == "\$ref" && value.isTextual) {
              visit(value.asText())
            }
            if (name == "discriminator" && value.isObject) {
              value.path("mapping").properties().forEach { (_, mapping) ->
                if (mapping.isTextual) {
                  visit(mapping.asText())
                }
              }
            }
            value.visitReferences(visit)
          }
        }

        isArray -> elements().forEachRemaining { element -> element.visitReferences(visit) }
      }
    }

    private fun resolveNativeReference(
      sourceFile: File,
      reference: String,
    ): File? {
      val referencedPath = reference.substringBefore('#')
      if (referencedPath.isBlank()) {
        return null
      }

      val referencedFile =
        runCatching {
          val uri = URI(referencedPath)
          when {
            uri.isAbsolute && uri.scheme.equals("file", ignoreCase = true) -> File(uri)
            uri.isAbsolute -> null
            else -> File(sourceFile.toURI().resolve(uri))
          }
        }.getOrElse {
          sourceFile.parentFile.resolve(referencedPath)
        } ?: return null

      return referencedFile
        .takeIf { it.isFile }
        ?.canonicalFile
    }

    private fun writeIndex(
      outputFile: File,
      files: Iterable<File>,
    ) {
      outputFile.parentFile.mkdirs()
      outputFile.writeText(
        files
          .map { it.absolutePath }
          .sorted()
          .joinToString("\n"),
      )
    }

    companion object {
      private val yamlMapper = ObjectMapper(YAMLFactory())
    }
  }
