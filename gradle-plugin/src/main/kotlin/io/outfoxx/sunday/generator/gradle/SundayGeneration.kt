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

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.Tolerance
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions.BaseUriMode
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME

class SundayGeneration(
  val name: String,
  objects: ObjectFactory,
  project: Project,
) {
  /** Persistent cache for public HTTP(S) OpenAPI documents, revalidated on each online build. */
  val openApiReferenceCacheDirectory: DirectoryProperty =
    objects.directoryProperty().fileValue(
      project.gradle.gradleUserHomeDir.resolve("caches/sunday/openapi"),
    )

  /** Allows private network destinations and configured proxies for trusted OpenAPI specifications. */
  val openApiAllowPrivateNetwork: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  val source: Property<FileCollection> =
    objects
      .property(FileCollection::class.java)
      .convention(project.fileTree("src/main/sunday") { it.include("**/*.raml") })

  @Deprecated(
    message = "Includes are discovered automatically; this property has no effect.",
    level = DeprecationLevel.WARNING,
  )
  val includes: Property<FileCollection> = objects.property(FileCollection::class.java)

  val framework: Property<TargetFramework> = objects.property(TargetFramework::class.java)
  val mode: Property<GenerationMode> = objects.property(GenerationMode::class.java)

  /** Master switch for API-derived application metadata. */
  val generateApplicationMetadata: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  /** Emit Quarkus server configuration metadata. */
  val generateServerConfiguration: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  /** Emit Quarkus client configuration metadata. */
  val generateClientConfiguration: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  /** Properties resource path for server configuration metadata. */
  val serverConfigurationFileName: Property<String> =
    objects
      .property(
        String::class.java,
      ).convention("META-INF/microprofile-config.properties")

  /** Properties resource path for client configuration metadata. */
  val clientConfigurationFileName: Property<String> =
    objects
      .property(
        String::class.java,
      ).convention("META-INF/microprofile-config.properties")

  /** Generate server configurations and required application transport factories. */
  val generateClientConfig: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  /** Explicit environment profile used by policy and security metadata. */
  val profile: Property<String> = objects.property(String::class.java)

  /** Default permitted directions for schema-declared tolerant values. */
  val defaultTolerance: Property<Tolerance> =
    objects.property(Tolerance::class.java).convention(Tolerance.Response)

  /** Derives patch companions for merge-patch request bodies without requiring a schema annotation. */
  val autoPatchable: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  val generateModel: Property<Boolean> = objects.property(Boolean::class.java)
  val generateService: Property<Boolean> = objects.property(Boolean::class.java)
  val generateBrokerServices: Property<Boolean> = objects.property(Boolean::class.java)
  val pkgName: Property<String> = objects.property(String::class.java)
  val servicePkgName: Property<String> = objects.property(String::class.java)
  val serviceSuffix: Property<String> = objects.property(String::class.java)
  val aggregateServices: Property<Boolean> = objects.property(Boolean::class.java)
  val aggregateServiceName: Property<String> = objects.property(String::class.java)
  val servicesFromTags: Property<Boolean> = objects.property(Boolean::class.java)
  val modelPkgName: Property<String> = objects.property(String::class.java)
  val problemBaseUri: Property<String> = objects.property(String::class.java)
  val problemLibrary: Property<KotlinProblemLibrary> = objects.property(KotlinProblemLibrary::class.java)
  val problemRfc: Property<KotlinProblemRfc> = objects.property(KotlinProblemRfc::class.java)
  val disableValidationConstraints: Property<Boolean> = objects.property(Boolean::class.java)
  val disableContainerElementValid: Property<Boolean> = objects.property(Boolean::class.java)
  val disableJacksonAnnotations: Property<Boolean> = objects.property(Boolean::class.java)
  val preserveUnknownFields: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
  val disableModelImplementations: Property<Boolean> = objects.property(Boolean::class.java)
  val coroutines: Property<Boolean> = objects.property(Boolean::class.java)
  val flowCoroutines: Property<Boolean> = objects.property(Boolean::class.java)
  val reactiveResponseType: Property<String> = objects.property(String::class.java)
  val explicitSecurityParameters: Property<Boolean> = objects.property(Boolean::class.java)
  val baseUriMode: Property<BaseUriMode> = objects.property(BaseUriMode::class.java)
  val defaultMediaTypes: ListProperty<String> = objects.listProperty(String::class.java)
  val generatedAnnotation: Property<String> = objects.property(String::class.java)
  val generationTimestamp: Property<String> = objects.property(String::class.java)
  val alwaysUseResponseReturn: Property<Boolean> = objects.property(Boolean::class.java)
  val useResultResponseReturn: Property<Boolean> = objects.property(Boolean::class.java)
  val useJakartaPackages: Property<Boolean> = objects.property(Boolean::class.java)
  val quarkus: Property<Boolean> = objects.property(Boolean::class.java)

  /** Generates JAX-RS endpoint implementations backed by application-owned service delegates. */
  val resourceAdapters: Property<Boolean> = objects.property(Boolean::class.java)

  /** Enforces named security schemes and permissions in generated resource adapters. */
  val enforceSecuritySchemes: Property<Boolean> = objects.property(Boolean::class.java)
  val outputDir: Property<Directory> = objects.directoryProperty()

  val targetSourceSet: Property<String> =
    objects
      .property(String::class.java)
      .convention(MAIN_SOURCE_SET_NAME)
}
