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

package io.outfoxx.sunday.generator.gradle.tests

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.appendText
import kotlin.streams.toList

class GradlePluginTests {

  val dualTestBuildFile =
    """
    import static io.outfoxx.sunday.generator.gradle.TargetFramework.*
    import static io.outfoxx.sunday.generator.GenerationMode.*
    import static io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary.*
    import static io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc.*

    plugins {
      id 'org.jetbrains.kotlin.jvm' version '2.3.10'
      id 'io.outfoxx.sunday-generator'
    }

    repositories {
      mavenCentral()
    }

    sundayGenerations {
      client {
        source.set(fileTree('src/main/sunday') { it.include('**/*.raml') })
        includes.set(fileTree('src/main/sunday-includes') { it.include('**/*.raml') })
        framework.set(Sunday)
        mode.set(Client)
        pkgName.set('io.outfoxx.test.client')
        modelPkgName.set('io.outfoxx.test.client.model')
        servicePkgName.set('io.outfoxx.test.client.api')
        generateModel.set(true)
        generateService.set(true)
        serviceSuffix.set('API')
        aggregateServices.set(false)
        aggregateServiceName.set('RootAPI')
        servicesFromTags.set(false)
        problemBaseUri.set('https://example.com/problems/')
        problemLibrary.set(SUNDAY)
        problemRfc.set(RFC9457)
        disableValidationConstraints.set(false)
        disableJacksonAnnotations.set(false)
        disableModelImplementations.set(false)
        coroutines.set(false)
        reactiveResponseType.set(null)
        explicitSecurityParameters.set(false)
        baseUriMode.set(null)
        defaultMediaTypes.set(["application/json"])
        generatedAnnotation.set('javax.annotation.processing.Generated')
        alwaysUseResponseReturn.set(false)
        useResultResponseReturn.set(false)
        useJakartaPackages.set(false)
      }
      server {
        framework.set(JAXRS)
        mode.set(Server)
        reactiveResponseType.set("${CompletableFuture::class.java.canonicalName}")
        pkgName.set('io.outfoxx.test.server')
      }
    }

    dependencies {
      implementation "io.outfoxx.sunday:sunday-core:2.0.0-beta.1"
      implementation "org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec:1.0.0.Final"
      implementation "javax.validation:validation-api:2.0.1.Final"
      implementation "com.fasterxml.jackson.core:jackson-databind:2.10.0"
    }

    java {
      sourceCompatibility = "21"
      targetCompatibility = "21"
    }

    compileKotlin {
      kotlinOptions {
        jvmTarget = "21"
        suppressWarnings = true
      }
    }

    if (hasProperty('buildScan')) {
        buildScan {
            termsOfServiceUrl = 'https://gradle.com/terms-of-service'
            termsOfServiceAgree = 'yes'
        }
    }

    """.trimIndent()

  val dualTestBuildFileNoIncludes =
    """
    import static io.outfoxx.sunday.generator.gradle.TargetFramework.*
    import static io.outfoxx.sunday.generator.GenerationMode.*
    import static io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary.*
    import static io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc.*

    plugins {
      id 'org.jetbrains.kotlin.jvm' version '2.3.10'
      id 'io.outfoxx.sunday-generator'
    }

    repositories {
      mavenCentral()
    }

    sundayGenerations {
      client {
        source.set(fileTree('src/main/sunday') { it.include('**/*.raml') })
        framework.set(Sunday)
        mode.set(Client)
        pkgName.set('io.outfoxx.test.client')
        modelPkgName.set('io.outfoxx.test.client.model')
        servicePkgName.set('io.outfoxx.test.client.api')
        generateModel.set(true)
        generateService.set(true)
        serviceSuffix.set('API')
        aggregateServices.set(false)
        aggregateServiceName.set('RootAPI')
        servicesFromTags.set(false)
        problemBaseUri.set('https://example.com/problems/')
        problemLibrary.set(SUNDAY)
        problemRfc.set(RFC9457)
        disableValidationConstraints.set(false)
        disableJacksonAnnotations.set(false)
        disableModelImplementations.set(false)
        coroutines.set(false)
        reactiveResponseType.set(null)
        explicitSecurityParameters.set(false)
        baseUriMode.set(null)
        defaultMediaTypes.set(["application/json"])
        generatedAnnotation.set('javax.annotation.processing.Generated')
        alwaysUseResponseReturn.set(false)
        useResultResponseReturn.set(false)
        useJakartaPackages.set(false)
      }
      server {
        framework.set(JAXRS)
        mode.set(Server)
        reactiveResponseType.set("${CompletableFuture::class.java.canonicalName}")
        pkgName.set('io.outfoxx.test.server')
      }
    }

    dependencies {
      implementation "io.outfoxx.sunday:sunday-core:2.0.0-beta.1"
      implementation "org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec:1.0.0.Final"
      implementation "javax.validation:validation-api:2.0.1.Final"
      implementation "com.fasterxml.jackson.core:jackson-databind:2.10.0"
    }

    java {
      sourceCompatibility = "21"
      targetCompatibility = "21"
    }

    compileKotlin {
      kotlinOptions {
        jvmTarget = "21"
        suppressWarnings = true
      }
    }

    if (hasProperty('buildScan')) {
        buildScan {
            termsOfServiceUrl = 'https://gradle.com/terms-of-service'
            termsOfServiceAgree = 'yes'
        }
    }

    """.trimIndent()

  private lateinit var buildFile: File

  @TempDir
  lateinit var testProjectDir: File

  @BeforeEach
  fun setup() {

    buildFile = testProjectDir.resolve("build.gradle")
    buildFile.createNewFile()

    copy("/test.raml", testProjectDir.resolve("src/main/sunday"))
  }

  @Test
  fun `generate Sunday client & JAX-RS server for Kotlin`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    buildFile.writeText(dualTestBuildFile)

    val result =
      GradleRunner
        .create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("build", "--stacktrace", "--debug")
        .withDebug(true)
        .build()

    val genClientTask = result.task(":sundayGenerate_client")
    assertThat(genClientTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val genServerTask = result.task(":sundayGenerate_server")
    assertThat(genServerTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val kotlinTask = result.task(":compileKotlin")
    assertThat(kotlinTask?.outcome, equalTo(TaskOutcome.SUCCESS))
  }

  @Test
  fun `generate sources for kotlin are cached`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    buildFile.writeText(dualTestBuildFile)

    // Generate settings.gradle.kts with temporary build cache dir
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      buildCache {
        local {
          directory = file("${testProjectDir.resolve("local-cache")}")
        }
      }
      """.trimIndent(),
    )

    fun runBuild() =
      GradleRunner
        .create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("build", "--stacktrace", "--debug", "--build-cache")
        .withDebug(true)
        .forwardOutput()
        .build()

    runBuild().also { result ->
      val resolverOutcome = anyOf(equalTo(TaskOutcome.SUCCESS), equalTo(TaskOutcome.FROM_CACHE))
      assertGenerationOutcome(result, "client", resolverOutcome, equalTo(TaskOutcome.SUCCESS))
      assertGenerationOutcome(result, "server", resolverOutcome, equalTo(TaskOutcome.SUCCESS))

      val kotlinTask = result.task(":compileKotlin")
      assertThat(kotlinTask?.outcome, equalTo(TaskOutcome.SUCCESS))
    }

    runBuild().also { result ->
      val resolverOutcome = anyOf(equalTo(TaskOutcome.SUCCESS), equalTo(TaskOutcome.FROM_CACHE))
      assertGenerationOutcome(result, "client", resolverOutcome, equalTo(TaskOutcome.UP_TO_DATE))
      assertGenerationOutcome(result, "server", resolverOutcome, equalTo(TaskOutcome.UP_TO_DATE))

      val kotlinTask = result.task(":compileKotlin")
      assertThat(kotlinTask?.outcome, equalTo(TaskOutcome.UP_TO_DATE))
    }

    runBuild().also { result ->
      assertGenerationOutcome(result, "client", equalTo(TaskOutcome.UP_TO_DATE), equalTo(TaskOutcome.UP_TO_DATE))
      assertGenerationOutcome(result, "server", equalTo(TaskOutcome.UP_TO_DATE), equalTo(TaskOutcome.UP_TO_DATE))

      val kotlinTask = result.task(":compileKotlin")
      assertThat(kotlinTask?.outcome, equalTo(TaskOutcome.UP_TO_DATE))
    }

    testProjectDir.resolve("build").deleteRecursively()

    runBuild().also { result ->
      assertGenerationOutcome(result, "client", equalTo(TaskOutcome.FROM_CACHE), equalTo(TaskOutcome.FROM_CACHE))
      assertGenerationOutcome(result, "server", equalTo(TaskOutcome.FROM_CACHE), equalTo(TaskOutcome.FROM_CACHE))

      val kotlinTask = result.task(":compileKotlin")
      assertThat(kotlinTask?.outcome, equalTo(TaskOutcome.FROM_CACHE))
    }
  }

  @Test
  fun `configuration cache is reused with build cache`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    buildFile.writeText(dualTestBuildFile)

    // Generate settings.gradle.kts with temporary build cache dir
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      buildCache {
        local {
          directory = file("${testProjectDir.resolve("local-cache")}")
        }
      }
      """.trimIndent(),
    )

    val first =
      try {
        GradleRunner
          .create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
          .withArguments("sundayGenerate_client", "--stacktrace", "--configuration-cache", "--build-cache")
          .withDebug(true)
          .build()
      } catch (ex: UnexpectedBuildFailure) {
        if (ex.message?.contains("support for using a Java agent with TestKit builds") == true) {
          assumeTrue(false, "Gradle TestKit does not support configuration cache with Java agents yet.")
        }
        throw ex
      }

    assertThat(first.output, not(containsString("Configuration cache problems found")))

    val second =
      try {
        GradleRunner
          .create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
          .withArguments("sundayGenerate_client", "--stacktrace", "--configuration-cache", "--build-cache")
          .withDebug(true)
          .build()
      } catch (ex: UnexpectedBuildFailure) {
        if (ex.message?.contains("support for using a Java agent with TestKit builds") == true) {
          assumeTrue(false, "Gradle TestKit does not support configuration cache with Java agents yet.")
        }
        throw ex
      }

    assertThat(second.output, containsString("Reusing configuration cache"))
    assertThat(second.output, not(containsString("Configuration cache problems found")))
  }

  @Test
  fun `Sunday client supports service tags and aggregate service options`() {

    val openApi = testProjectDir.resolve("src/main/sunday/tagged-api.yaml")
    openApi.parentFile.mkdirs()
    openApi.writeText(
      """
      openapi: 3.1.0
      info:
        title: Tagged API
        version: 1.0.0
      paths:
        /users/{userId}:
          get:
            operationId: getUser
            tags: [Users]
            parameters:
              - name: userId
                in: path
                required: true
                schema:
                  type: string
            responses:
              '200':
                description: User
                content:
                  application/json:
                    schema:
                      type: string
        /projects/{projectId}:
          get:
            operationId: getProject
            tags: [Projects]
            parameters:
              - name: projectId
                in: path
                required: true
                schema:
                  type: string
            responses:
              '200':
                description: Project
                content:
                  application/json:
                    schema:
                      type: string
      """.trimIndent(),
    )

    buildFile.writeText(
      """
      import static io.outfoxx.sunday.generator.gradle.TargetFramework.*
      import static io.outfoxx.sunday.generator.GenerationMode.*

      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.10'
        id 'io.outfoxx.sunday-generator'
      }

      repositories {
        mavenCentral()
      }

      sundayGenerations {
        client {
          source.set(fileTree('src/main/sunday') { it.include('**/*.yaml') })
          framework.set(Sunday)
          mode.set(Client)
          pkgName.set('io.outfoxx.test.client')
          servicePkgName.set('io.outfoxx.test.client.api')
          servicesFromTags.set(true)
          aggregateServices.set(true)
          aggregateServiceName.set('TaggedAPI')
          defaultMediaTypes.set(["application/json"])
        }
      }
      """.trimIndent(),
    )

    val result =
      GradleRunner
        .create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("sundayGenerate_client", "--stacktrace")
        .withDebug(true)
        .build()

    val genClientTask = result.task(":sundayGenerate_client")
    assertThat(genClientTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val outputDir = testProjectDir.resolve("build/generated/sources/sunday/sundayGenerate_client")
    val generatedFiles =
      Files.walk(outputDir.toPath()).use { paths ->
        paths
          .filter { path -> path.toFile().isFile }
          .map { path -> path.fileName.toString() }
          .toList()
      }

    assertThat(generatedFiles.contains("UsersAPI.kt"), equalTo(true))
    assertThat(generatedFiles.contains("ProjectsAPI.kt"), equalTo(true))
    assertThat(generatedFiles.contains("TaggedAPI.kt"), equalTo(true))
  }

  @Test
  fun `generate sources for kotlin are regenerated when sources changes`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    buildFile.writeText(dualTestBuildFile)

    // Generate settings.gradle.kts with temporary build cache dir
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      buildCache {
        local {
          directory = file("${testProjectDir.resolve("local-cache")}")
        }
      }
      """.trimIndent(),
    )

    listOf(TaskOutcome.SUCCESS, TaskOutcome.SUCCESS).forEach { outcome ->
      println("Executing build expecting outcome: $outcome")

      // Change contents of test.raml to force regeneration
      testProjectDir.resolve("src/main/sunday/test.raml").toPath().appendText("\n# Added comment\n")

      val result =
        GradleRunner
          .create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
          .withArguments("build", "--stacktrace", "--debug", "--build-cache")
          .withDebug(true)
          .forwardOutput()
          .build()

      assertGenerationInvalidated(result, "client")
      assertGenerationInvalidated(result, "server")

      val kotlinTask = result.task(":compileKotlin")
      assertThat(
        kotlinTask?.outcome,
        anyOf(equalTo(outcome), equalTo(TaskOutcome.FROM_CACHE), equalTo(TaskOutcome.UP_TO_DATE)),
      )
    }
  }

  @Test
  fun `generate sources for kotlin are regenerated when includes changes`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    val sourceDir = testProjectDir.resolve("src/main/sunday")
    sourceDir.mkdirs()

    val includesDir = testProjectDir.resolve("src/main/sunday-includes")
    includesDir.mkdirs()

    val nestedInclude = includesDir.resolve("nested_include.raml")
    nestedInclude.writeText(
      """
      #%RAML 1.0 DataType
      type: object
      properties:
        value: string
      """.trimIndent(),
    )

    val include = includesDir.resolve("include1.raml")
    include.writeText(
      """
      #%RAML 1.0 DataType
      type: object
      properties:
        id: string
        nested: !include nested_include.raml
      """.trimIndent(),
    )

    val mainFile = sourceDir.resolve("main_file.raml")
    mainFile.writeText(
      """
      #%RAML 1.0
      title: Test
      version: 1.0.0
      baseUri: http://localhost:8080/api
      mediaType: application/json
      types:
        Test:
          type: !include ../sunday-includes/include1.raml
      """.trimIndent(),
    )

    buildFile.writeText(dualTestBuildFileNoIncludes)

    // Generate settings.gradle.kts with temporary build cache dir
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      buildCache {
        local {
          directory = file("${testProjectDir.resolve("local-cache")}")
        }
      }
      """.trimIndent(),
    )

    GradleRunner
      .create()
      .withProjectDir(testProjectDir)
      .withPluginClasspath()
      .withArguments("build", "--stacktrace", "--debug", "--build-cache")
      .withDebug(true)
      .build()

    listOf(
      mainFile to "main",
      include to "include1",
      nestedInclude to "nested",
    ).forEach { (file, label) ->
      println("Executing build after updating $label file")

      file.appendText("\n# Updated $label\n")

      val result =
        GradleRunner
          .create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
          .withArguments("build", "--stacktrace", "--debug", "--build-cache")
          .withDebug(true)
          .forwardOutput()
          .build()

      assertGenerationInvalidated(result, "client")
      assertGenerationInvalidated(result, "server")

      val kotlinTask = result.task(":compileKotlin")
      assertThat(
        kotlinTask?.outcome,
        anyOf(equalTo(TaskOutcome.SUCCESS), equalTo(TaskOutcome.FROM_CACHE), equalTo(TaskOutcome.UP_TO_DATE)),
      )
    }
  }

  @Test
  fun `discover includes ignores RAML fragments in source`() {

    buildFile.writeText(dualTestBuildFileNoIncludes)

    val sourceDir = testProjectDir.resolve("src/main/sunday")
    sourceDir.mkdirs()

    sourceDir.resolve("main_file.raml").writeText(
      """
      #%RAML 1.0
      title: Test
      version: 1.0.0
      baseUri: http://localhost:8080/api
      mediaType: application/json
      types:
        Test:
          type: object
          properties:
            id: string
      """.trimIndent(),
    )

    sourceDir.resolve("bad_fragment.raml").writeText(
      """
      #%RAML 1.0 DataType
      type: [this is invalid
      """.trimIndent(),
    )

    val result =
      GradleRunner
        .create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("sundayDiscoverIncludes_client", "--stacktrace", "--debug")
        .withDebug(true)
        .build()

    val discoverTask = result.task(":sundayDiscoverIncludes_client")
    assertThat(discoverTask?.outcome, equalTo(TaskOutcome.SUCCESS))
  }

  @Test
  fun `disable container element validation applies use-site constraints`() {
    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))
    copy("/validation-constraints.raml", testProjectDir.resolve("src/main/sunday"))

    buildFile.writeText(
      dualTestBuildFile +
        "\n" +
        """
        sundayGenerations {
          client {
            disableContainerElementValid.set(true)
          }
        }
        """.trimIndent(),
    )

    val result =
      GradleRunner
        .create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("build", "--stacktrace", "--debug")
        .withDebug(true)
        .build()

    val genClientTask = result.task(":sundayGenerate_client")
    assertThat(genClientTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val outputDir = testProjectDir.resolve("build/generated/sources/sunday/sundayGenerate_client")
    val modelPath =
      Files.walk(outputDir.toPath()).use { paths ->
        paths
          .filter { path -> path.fileName.toString() == "ValidationTest.kt" }
          .findFirst()
          .orElseThrow { FileNotFoundException("ValidationTest.kt not found under $outputDir") }
      }

    val source = modelPath.toFile().readText()
    assertThat(source, containsString("@field:Size("))
    assertThat(source, containsString("@field:Pattern("))
    assertThat(source, containsString("public val codes: List<String>"))
    assertThat(source, not(containsString("List<@Size")))
  }

  private fun copy(
    src: String,
    dstDir: File,
  ) {
    dstDir.mkdirs()

    val srcPath = Paths.get(GradlePluginTests::class.java.getResource(src)?.toURI() ?: error("Resource not found"))
    val dstPath = dstDir.toPath()
    Files.copy(srcPath, dstPath.resolve(srcPath.fileName))
  }

  private fun assertGenerationOutcome(
    result: BuildResult,
    generationName: String,
    resolverOutcome: Matcher<TaskOutcome>,
    generatorOutcome: Matcher<TaskOutcome>,
  ) {
    val discoverTask = result.task(":sundayDiscoverIncludes_$generationName")
    assertThat(discoverTask?.outcome, resolverOutcome)

    val generateTask = result.task(":sundayGenerate_$generationName")
    assertThat(generateTask?.outcome, generatorOutcome)
  }

  private fun assertGenerationInvalidated(
    result: BuildResult,
    generationName: String,
  ) {
    assertGenerationOutcome(
      result,
      generationName,
      anyOf(equalTo(TaskOutcome.SUCCESS), equalTo(TaskOutcome.FROM_CACHE)),
      anyOf(equalTo(TaskOutcome.SUCCESS), equalTo(TaskOutcome.FROM_CACHE)),
    )
  }
}
