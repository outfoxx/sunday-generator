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

package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedJaxrs
import io.outfoxx.sunday.generator.ir.GeneratedJaxrsRestClient
import io.outfoxx.sunday.generator.ir.GeneratedModeFlag
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedNullify
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedPolicy
import io.outfoxx.sunday.generator.ir.GeneratedProtocol
import io.outfoxx.sunday.generator.ir.GeneratedProtocolBinding
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.GeneratedZanzibarJwtUserSource
import io.outfoxx.sunday.generator.ir.GeneratedZanzibarUserSource
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.UseJakartaPackages
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.kotlin.tools.compileTypes
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.kotlin.utils.kotlinFileSpec
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertKotlinJaxrsSnapshot
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletionStage

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [IR] Generator Test")
class KotlinJAXRSIrGeneratorTest {

  @Test
  fun `Kotlin JAX-RS CLI uses the IR exporter directly`() {
    val source =
      Path
        .of("../cli/src/main/kotlin/io/outfoxx/sunday/generator/kotlin/KotlinJAXRSGenerateCommand.kt")
        .toFile()
        .readText()

    assertTrue(source.contains("GeneratedApiIrExporter"))
    assertTrue(source.contains("KotlinJAXRSIrGenerator(api, typeRegistry, kotlinJaxrsOptions())"))
    assertFalse(source.contains("KotlinJAXRSGenerator("), source)
  }

  @Test
  fun `Kotlin JAX-RS IR renderer does not read AMF service model types`() {
    val source =
      Path
        .of("src/main/kotlin/io/outfoxx/sunday/generator/kotlin/KotlinJAXRSIrGenerator.kt")
        .toFile()
        .readText()

    assertFalse(source.contains("import amf."), source)
    assertFalse(source.contains("amf.apicontract.client.platform.model.domain.EndPoint"), source)
    assertFalse(source.contains("amf.apicontract.client.platform.model.domain.Operation"), source)
    assertFalse(source.contains("amf.apicontract.client.platform.model.domain.Parameter"), source)
    assertFalse(source.contains("amf.apicontract.client.platform.model.domain.Response"), source)
    assertFalse(source.contains("amf.core.client.platform.model.domain.Shape"), source)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus streaming request bodies as Mutiny buffer streams by target mode`() {
    listOf(
      GenerationMode.Client to
        mapOf(
          "streamClient" to true,
          "streamServer" to false,
          "streamAll" to true,
          "streamNullable" to true,
          "uploadRegular" to false,
        ),
      GenerationMode.Server to
        mapOf(
          "streamClient" to false,
          "streamServer" to true,
          "streamAll" to true,
          "streamNullable" to true,
          "uploadRegular" to false,
        ),
    ).forEach { (mode, expectations) ->
      val typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          mode,
          setOf(),
          problemLibrary = KotlinProblemLibrary.QUARKUS,
          problemRfc = KotlinProblemRfc.RFC9457,
        )

      KotlinJAXRSIrGenerator(streamingRequestBodyApi(), typeRegistry, testOptions(quarkus = true))
        .generateServiceTypes()

      val builtTypes = typeRegistry.buildTypes()
      val source = kotlinSource("io.test.service", findType("io.test.service.UploadsAPI", builtTypes))

      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
      assertTrue(source.contains("import io.smallrye.mutiny.Multi"), source)
      assertTrue(source.contains("import io.vertx.mutiny.core.buffer.Buffer"), source)
      expectations.forEach { (operationId, streaming) ->
        val expectedBody = if (streaming) "body: Multi<Buffer>" else "body: ByteArray"
        assertTrue(source.contains("public fun $operationId($expectedBody)"), source)
      }
      if (mode == GenerationMode.Client) {
        assertTrue(source.contains("public fun streamNullableOrNull(body: Multi<Buffer>)"), source)
      }
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `keeps streaming request bodies as normal body parameters for non Quarkus JAX-RS`() {
    listOf(GenerationMode.Client, GenerationMode.Server).forEach { mode ->
      val typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          mode,
          setOf(),
          problemLibrary = KotlinProblemLibrary.ZALANDO,
          problemRfc = KotlinProblemRfc.RFC7807,
        )

      KotlinJAXRSIrGenerator(streamingRequestBodyApi(), typeRegistry, testOptions(quarkus = false))
        .generateServiceTypes()

      val builtTypes = typeRegistry.buildTypes()
      val source = kotlinSource("io.test.service", findType("io.test.service.UploadsAPI", builtTypes))

      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
      assertFalse(source.contains("Multi<Buffer>"), source)
      assertTrue(source.contains("public fun streamServer(body: ByteArray)"), source)
      assertTrue(source.contains("public fun streamAll(body: ByteArray)"), source)
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates AsyncAPI subscribe event streams as JAX-RS GET methods`() {
    val typeRegistry = clientTypeRegistry()
    val api = eventStreamApi()

    KotlinJAXRSIrGenerator(api, typeRegistry, kotlinJAXRSTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("io.test.service", findType("io.test.service.EventsAPI", builtTypes))
          .writeTo(this)
      }

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(source.contains("@GET"))
    assertTrue(source.contains("public fun streamEvents(): SseEventSource"))
    assertFalse(source.contains("Flow<EventEnvelope>"))
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus event streams as Mutiny Multi in client and server modes`() {
    listOf(GenerationMode.Client, GenerationMode.Server).forEach { mode ->
      val typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          mode,
          setOf(),
          problemLibrary = KotlinProblemLibrary.ZALANDO,
          problemRfc = KotlinProblemRfc.RFC7807,
        )

      KotlinJAXRSIrGenerator(
        eventStreamApi(),
        typeRegistry,
        testOptions(
          quarkus = true,
          coroutineFlowMethods = true,
          coroutineServiceMethods = true,
        ),
      ).generateServiceTypes()

      val builtTypes = typeRegistry.buildTypes()
      val source =
        buildString {
          FileSpec
            .get("io.test.service", findType("io.test.service.EventsAPI", builtTypes))
            .writeTo(this)
        }

      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
      assertTrue(source.contains("import io.smallrye.mutiny.Multi"), source)
      assertTrue(source.contains("public fun streamEvents(): Multi<EventEnvelope>"), source)
      assertFalse(source.contains("suspend fun streamEvents"), source)
      assertFalse(source.contains("Flow<EventEnvelope>"), source)
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `skips non-HTTP AsyncAPI channels for JAX-RS resources`() {
    val typeRegistry = typeRegistry()

    KotlinJAXRSIrGenerator(protocolMixedEventApi(), typeRegistry, testOptions(quarkus = true))
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val eventsSource = kotlinSource("io.test.service", findType("io.test.service.EventsAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(eventsSource.contains("@Path(value = \"/events\")"), eventsSource)
    assertTrue(eventsSource.contains("public fun streamEvents(): Multi<EventEnvelope>"), eventsSource)
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "PlatformEventsAPI" })
    assertFalse(eventsSource.contains("platform.events"), eventsSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `flattens direct AsyncAPI discriminated event object unions in implement-model mode`(
    @ResourceUri("asyncapi/ir/direct-discriminated-event-union.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(ImplementModel, JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    KotlinJAXRSIrGenerator(api, typeRegistry, testOptions())
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val accountsTeamCreatedEventSource =
      kotlinSource("io.test", findType("io.test.AccountsTeamCreatedEvent", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(accountsTeamCreatedEventSource.contains("public data class AccountsTeamCreatedEvent"))
    assertTrue(accountsTeamCreatedEventSource.contains(": EventEnvelope"))
    assertTrue(accountsTeamCreatedEventSource.contains("public val id: String"), accountsTeamCreatedEventSource)
    assertTrue(accountsTeamCreatedEventSource.contains("public val `data`: AccountsTeamCreatedData"))
    assertFalse(accountsTeamCreatedEventSource.contains(": BaseEventEnvelope("), accountsTeamCreatedEventSource)
    assertFalse(accountsTeamCreatedEventSource.contains("Map<String, Any>"), accountsTeamCreatedEventSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus throwable source problem models from IR`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(ImplementModel, JacksonAnnotations, UseJakartaPackages),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )

    KotlinJAXRSIrGenerator(sourceProblemModelApi(), typeRegistry, testOptions(quarkus = true))
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val httpProblemSource = kotlinSource("io.test", findType("io.test.HttpProblem", builtTypes))
    val graphsProblemSource = kotlinSource("io.test", findType("io.test.GraphsProblem", builtTypes))
    val repoNotFoundSource = kotlinSource("io.test", findType("io.test.RepoNotFoundProblem", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(
      httpProblemSource.contains("import io.quarkiverse.resteasy.problem.HttpProblem as QuarkusHttpProblem"),
      httpProblemSource,
    )
    assertTrue(httpProblemSource.contains(": QuarkusHttpProblem("), httpProblemSource)
    assertTrue(httpProblemSource.contains("QuarkusHttpProblem.builder()"), httpProblemSource)
    assertTrue(httpProblemSource.contains("builder.withType(type)"), httpProblemSource)
    assertTrue(httpProblemSource.contains("builder.withStatus(status)"), httpProblemSource)
    assertFalse(httpProblemSource.contains("public val type"), httpProblemSource)

    assertTrue(
      graphsProblemSource.contains("import io.quarkiverse.resteasy.problem.HttpProblem as QuarkusHttpProblem"),
      graphsProblemSource,
    )
    assertTrue(graphsProblemSource.contains(": QuarkusHttpProblem("), graphsProblemSource)
    assertTrue(graphsProblemSource.contains("builder.with(\"code\", code)"), graphsProblemSource)
    assertFalse(graphsProblemSource.contains("public val type"), graphsProblemSource)
    assertTrue(repoNotFoundSource.contains(": GraphsProblem("), repoNotFoundSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates JAX-RS throwable source problem models from IR`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(ImplementModel, JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    KotlinJAXRSIrGenerator(sourceProblemModelApi(), typeRegistry, testOptions())
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val graphsProblemSource = kotlinSource("io.test", findType("io.test.GraphsProblem", builtTypes))
    val repoNotFoundSource = kotlinSource("io.test", findType("io.test.RepoNotFoundProblem", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(graphsProblemSource.contains("AbstractThrowableProblem("), graphsProblemSource)
    assertTrue(graphsProblemSource.contains("Status.valueOf(status)"), graphsProblemSource)
    assertFalse(graphsProblemSource.contains("public val type"), graphsProblemSource)
    assertTrue(repoNotFoundSource.contains(": GraphsProblem("), repoNotFoundSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus required query validation without empty class path`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(ValidationConstraints),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )

    KotlinJAXRSIrGenerator(requiredQueryApi(), typeRegistry, testOptions(quarkus = true))
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertFalse(source.contains("@Path(value = \"\")"), source)
    assertTrue(source.contains("import javax.validation.constraints.NotNull"), source)
    assertTrue(source.contains("@RestQuery @NotNull @Size(min = 2) q: String"), source)
  }

  private fun typeRegistry(): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Server,
      setOf(),
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  private fun eventStreamApi(): GeneratedApi =
    GeneratedApi(
      name = "Composed API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory://events"),
      services =
        listOf(
          GeneratedService(
            name = "EventsService",
            operations =
              listOf(
                GeneratedOperation(
                  id = "streamEvents",
                  method = "SUBSCRIBE",
                  path = "/events",
                  responses =
                    listOf(
                      GeneratedResponse(
                        type = GeneratedTypeRef.named("EventEnvelope"),
                        mediaTypes = listOf("application/json"),
                      ),
                    ),
                  streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
                ),
              ),
          ),
        ),
      models =
        listOf(
          GeneratedModel(
            name = "EventEnvelope",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty(
                  name = "id",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
              ),
          ),
        ),
    )

  private fun protocolMixedEventApi(): GeneratedApi =
    GeneratedApi(
      name = "Composed API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory://events"),
      services =
        listOf(
          GeneratedService(
            name = "EventsService",
            operations =
              listOf(
                GeneratedOperation(
                  id = "streamEvents",
                  method = "SUBSCRIBE",
                  path = "/events",
                  responses =
                    listOf(
                      GeneratedResponse(
                        type = GeneratedTypeRef.named("EventEnvelope"),
                        mediaTypes = listOf("application/json"),
                      ),
                    ),
                  streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
                ),
              ),
          ),
          GeneratedService(
            name = "PlatformEventsService",
            operations =
              listOf(
                GeneratedOperation(
                  id = "consumePlatformEvent",
                  method = "SUBSCRIBE",
                  path = "platform.events",
                  responses =
                    listOf(
                      GeneratedResponse(
                        type = GeneratedTypeRef.named("EventEnvelope"),
                        mediaTypes = listOf("application/json"),
                      ),
                    ),
                  protocol =
                    GeneratedProtocol(
                      bindings =
                        listOf(
                          GeneratedProtocolBinding(
                            GeneratedProtocolBinding.Kind.CHANNEL,
                            "amqp",
                          ),
                        ),
                    ),
                  streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
                ),
              ),
          ),
        ),
      models =
        listOf(
          GeneratedModel(
            name = "EventEnvelope",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty(
                  name = "id",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
              ),
          ),
        ),
    )

  private fun sourceProblemModelApi(): GeneratedApi =
    GeneratedApi(
      name = "Problem API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://problems"),
      models =
        listOf(
          GeneratedModel(
            name = "HttpProblem",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri")),
                GeneratedModelProperty("title", GeneratedTypeRef.scalar("string")),
                GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer")),
                GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string")),
                GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string", format = "uri")),
              ),
          ),
          GeneratedModel(
            name = "GraphsProblem",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
          GeneratedModel(
            name = "RepoNotFoundProblem",
            kind = GeneratedModel.Kind.OBJECT,
            inherits = listOf(GeneratedTypeRef.named("GraphsProblem")),
            properties =
              listOf(
                GeneratedModelProperty("repoId", GeneratedTypeRef.scalar("string")),
              ),
          ),
        ),
    )

  private fun requiredQueryApi(): GeneratedApi =
    GeneratedApi(
      name = "Query API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://query"),
      services =
        listOf(
          GeneratedService(
            name = "UsersService",
            baseUri = "https://api.example.com",
            operations =
              listOf(
                GeneratedOperation(
                  id = "searchUsers",
                  method = "GET",
                  path = "/users/search",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "q",
                        location = GeneratedParameter.Location.QUERY,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                        validation = mapOf("minLength" to "2"),
                      ),
                    ),
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
              ),
          ),
        ),
    )

  private fun streamingRequestBodyApi(): GeneratedApi =
    GeneratedApi(
      name = "Uploads API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://uploads"),
      services =
        listOf(
          GeneratedService(
            name = "Uploads",
            operations =
              listOf(
                streamingRequestBodyOperation("streamClient", GeneratedModeFlag(client = true)),
                streamingRequestBodyOperation("streamServer", GeneratedModeFlag(server = true)),
                streamingRequestBodyOperation("streamAll", GeneratedModeFlag(all = true)),
                streamingRequestBodyOperation(
                  "streamNullable",
                  GeneratedModeFlag(all = true),
                  nullify = GeneratedNullify(statuses = listOf(404)),
                  responseBodyType = GeneratedTypeRef.scalar("string"),
                ),
                streamingRequestBodyOperation("uploadRegular", null),
              ),
          ),
        ),
    )

  private fun streamingRequestBodyOperation(
    id: String,
    streaming: GeneratedModeFlag?,
    nullify: GeneratedNullify? = null,
    responseBodyType: GeneratedTypeRef? = null,
  ): GeneratedOperation =
    GeneratedOperation(
      id = id,
      method = "PUT",
      path = "/$id",
      requestBody =
        GeneratedPayload(
          type = GeneratedTypeRef.scalar("file"),
          mediaTypes = listOf("application/octet-stream"),
          streaming = streaming,
        ),
      responses =
        listOf(
          GeneratedResponse(
            status = if (responseBodyType == null) 204 else 200,
            type = responseBodyType,
          ),
        ),
      nullify = nullify,
    )

  private fun clientTypeRegistry(): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Client,
      setOf(),
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  private fun routingContextStub(): Map<ClassName, TypeSpec> =
    mapOf(
      ClassName("io.vertx.ext.web", "RoutingContext") to TypeSpec.interfaceBuilder("RoutingContext").build(),
    )

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates request methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    val typeRegistry = typeRegistry()
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    KotlinJAXRSIrGenerator(api, typeRegistry, kotlinJAXRSTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertKotlinJaxrsSnapshot(
      "RequestMethodsTest/test-request-method-generation-in-server-mode.output.kt",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/service/API.kt"),
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates client request methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    val typeRegistry = clientTypeRegistry()
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    KotlinJAXRSIrGenerator(api, typeRegistry, kotlinJAXRSTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertKotlinJaxrsSnapshot(
      "RequestMethodsTest/test-request-method-generation-in-client-mode.output.kt",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/service/API.kt"),
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates URI parameters from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ValidationConstraints)),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "RequestUriParamsTest/test-basic-uri-parameter-generation-with-validation-constraints.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates optional query parameters from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf()),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "RequestQueryParamsTest/test-optional-query-parameter-generation.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates constant client headers from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf()),
      options =
        KotlinJAXRSOptions(
          coroutineFlowMethods = false,
          coroutineServiceMethods = false,
          reactiveResponseType = null,
          explicitSecurityParameters = false,
          baseUriMode = null,
          alwaysUseResponseReturn = false,
          defaultServicePackageName = "io.test.service",
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = true,
        ),
      filePackageName = "io.test.service",
      snapshotPath =
        "RequestHeaderParamsTest/test-constant-header-parameter-generation-with-quarkus-option-enabled-in-client-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates concrete client models from IR with and without Quarkus`() {
    listOf(false, true).forEach { quarkus ->
      val typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          setOf(ImplementModel),
          problemLibrary = KotlinProblemLibrary.ZALANDO,
          problemRfc = KotlinProblemRfc.RFC7807,
        )

      KotlinJAXRSIrGenerator(clientModelApi(), typeRegistry, testOptions(quarkus = quarkus))
        .generateServiceTypes()

      val builtTypes = typeRegistry.buildTypes()
      val baseSource = kotlinSource("io.test", findType("io.test.BaseProfile", builtTypes))
      val userSource = kotlinSource("io.test", findType("io.test.UserProfile", builtTypes))

      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
      assertTrue(baseSource.contains("public open class BaseProfile("), baseSource)
      assertTrue(userSource.contains("public class UserProfile("), userSource)
      assertTrue(userSource.contains(": BaseProfile("), userSource)
      assertFalse(userSource.contains("public val id:"), userSource)
      assertTrue(userSource.contains("public val displayName:"), userSource)
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates dynamic client content type parameters from IR with and without Quarkus`() {
    listOf(false, true).forEach { quarkus ->
      val typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          setOf(),
          problemLibrary = KotlinProblemLibrary.ZALANDO,
          problemRfc = KotlinProblemRfc.RFC7807,
        )

      KotlinJAXRSIrGenerator(contentTypeApi(), typeRegistry, testOptions(quarkus = quarkus))
        .generateServiceTypes()

      val builtTypes = typeRegistry.buildTypes()
      val serviceSource = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))
      val enumSource = kotlinSource("io.test", findType("io.test.AvatarContentType", builtTypes))

      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
      assertFalse(serviceSource.contains("@Consumes(value = [\"application/octet-stream\"])"), serviceSource)
      assertTrue(serviceSource.contains("(value = \"Content-Type\") contentType: AvatarContentType"), serviceSource)
      assertTrue(enumSource.contains("ImagePng(\"image/png\")"), enumSource)
      assertTrue(enumSource.contains("override fun toString(): String = wireValue"), enumSource)
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `omits server content type header parameters and wires enum JSON values`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    KotlinJAXRSIrGenerator(contentTypeApi(), typeRegistry, testOptions(quarkus = true))
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))
    val enumSource = kotlinSource("io.test", findType("io.test.AvatarContentType", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(
      serviceSource.contains("@Consumes(value = [\"image/png\", \"image/jpeg\", \"image/webp\"])"),
      serviceSource,
    )
    assertFalse(serviceSource.contains("contentType: AvatarContentType"), serviceSource)
    assertTrue(enumSource.contains("@JsonValue"), enumSource)
    assertTrue(enumSource.contains("@JsonCreator"), enumSource)
    assertTrue(enumSource.contains("@JvmStatic"), enumSource)
    assertTrue(enumSource.contains("public fun fromValue(rawValue: String): AvatarContentType"), enumSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates object unions and polymorphic event bases from IR across JAX-RS modes`() {
    listOf(GenerationMode.Client, GenerationMode.Server).forEach { mode ->
      listOf(false, true).forEach { quarkus ->
        val typeRegistry =
          KotlinTypeRegistry(
            "io.test",
            null,
            mode,
            setOf(ImplementModel, JacksonAnnotations),
            problemLibrary = KotlinProblemLibrary.ZALANDO,
            problemRfc = KotlinProblemRfc.RFC7807,
          )

        KotlinJAXRSIrGenerator(unionAndEventsApi(), typeRegistry, testOptions(quarkus = quarkus))
          .generateServiceTypes()

        val builtTypes = typeRegistry.buildTypes()
        val usersSource = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))
        val unionSource = kotlinSource("io.test", findType("io.test.UserResponse", builtTypes))
        val selfSource = kotlinSource("io.test", findType("io.test.UserSelfResponse", builtTypes))
        val eventEnvelopeSource = kotlinSource("io.test", findType("io.test.EventEnvelope", builtTypes))
        val eventDataSource = kotlinSource("io.test", findType("io.test.EventData", builtTypes))
        val teamCreatedSource = kotlinSource("io.test", findType("io.test.AccountsTeamCreatedData", builtTypes))
        val emptyRequestSource =
          kotlinSource("io.test", findType("io.test.AssignPullRequestReviewerRequest", builtTypes))

        assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
        if (mode == GenerationMode.Client) {
          assertTrue(usersSource.contains("public fun getUser(): UserResponse"), usersSource)
        } else {
          assertTrue(usersSource.contains("public fun getUser():"), usersSource)
        }
        assertTrue(usersSource.contains("body: AssignPullRequestReviewerRequest"), usersSource)
        assertTrue(unionSource.contains("public sealed interface UserResponse"), unionSource)
        assertTrue(unionSource.contains("@JsonDeserialize(using = UserResponse.Deserializer::class)"), unionSource)
        assertTrue(selfSource.contains(": UserResponse"), selfSource)
        assertTrue(selfSource.contains("@JsonDeserialize(using = JsonDeserializer.None::class)"), selfSource)
        assertTrue(eventEnvelopeSource.contains("public val `data`: EventData"), eventEnvelopeSource)
        assertTrue(eventEnvelopeSource.contains("@param:JsonTypeInfo("), eventEnvelopeSource)
        assertTrue(eventEnvelopeSource.contains("@get:JsonTypeInfo("), eventEnvelopeSource)
        assertTrue(eventEnvelopeSource.contains("JsonTypeInfo.As.EXTERNAL_PROPERTY"), eventEnvelopeSource)
        assertTrue(eventEnvelopeSource.contains("property = \"type\""), eventEnvelopeSource)
        assertTrue(
          eventEnvelopeSource
            .contains(
              "JsonSubTypes.Type(value = AccountsTeamCreatedData::class, name = \"accounts.team.created\")",
            ),
          eventEnvelopeSource,
        )
        assertFalse(eventEnvelopeSource.contains("Map<String, Any>"), eventEnvelopeSource)
        assertTrue(eventDataSource.contains("JsonTypeInfo.As.EXTERNAL_PROPERTY"), eventDataSource)
        assertTrue(
          eventDataSource
            .contains(
              "JsonSubTypes.Type(value = AccountsTeamCreatedData::class, name = \"accounts.team.created\")",
            ),
          eventDataSource,
        )
        assertTrue(teamCreatedSource.contains(": EventData("), teamCreatedSource)
        assertFalse(teamCreatedSource.contains(": Map<String, Any>("), teamCreatedSource)
        assertTrue(emptyRequestSource.contains("public class AssignPullRequestReviewerRequest()"), emptyRequestSource)
      }
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates bean validation annotations on IR model properties`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(ImplementModel, ValidationConstraints),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    KotlinJAXRSIrGenerator(validationModelApi(), typeRegistry, kotlinJAXRSTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val requestSource = kotlinSource("io.test", findType("io.test.CreateUserRequest", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(requestSource.contains("@get:Email"), requestSource)
    assertTrue(requestSource.contains("@get:Size(min = 8)"), requestSource)
    assertTrue(requestSource.contains("@get:Pattern(regexp = \"\"\"^[A-Za-z0-9_]+$\"\"\")"), requestSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `lowers supported IR scalar formats to Kotlin JAX-RS types`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(ImplementModel),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    KotlinJAXRSIrGenerator(formatApi(), typeRegistry, testOptions())
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source = kotlinSource("io.test", findType("io.test.FormatModel", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(source.contains("public val createdAt: OffsetDateTime"), source)
    assertTrue(source.contains("public val localCreatedAt: LocalDateTime"), source)
    assertTrue(source.contains("public val birthday: LocalDate"), source)
    assertTrue(source.contains("public val startsAt: LocalTime"), source)
    assertTrue(source.contains("public val targetUrl: URI"), source)
    assertTrue(source.contains("public val id: UUID"), source)
    assertTrue(source.contains("public val anyValue: Any"), source)
    assertTrue(source.contains("public val title: String"), source)
    assertTrue(source.contains("public val enabled: Boolean"), source)
    assertTrue(source.contains("public val defaultCount: Int"), source)
    assertTrue(source.contains("public val smallCount: Byte"), source)
    assertTrue(source.contains("public val mediumCount: Short"), source)
    assertTrue(source.contains("public val count: Int"), source)
    assertTrue(source.contains("public val aliasCount: Int"), source)
    assertTrue(source.contains("public val largeCount: Long"), source)
    assertTrue(source.contains("public val aliasLargeCount: Long"), source)
    assertTrue(source.contains("public val legacyCount: Int"), source)
    assertTrue(source.contains("public val legacyLargeCount: Long"), source)
    assertTrue(source.contains("public val directLongCount: Long"), source)
    assertTrue(source.contains("public val ratio: Double"), source)
    assertTrue(source.contains("public val preciseRatio: Double"), source)
    assertTrue(source.contains("public val filePayload: ByteArray"), source)
    assertTrue(source.contains("public val emptyValue: Unit"), source)
    assertTrue(source.contains("public val unknownValue: String"), source)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus fault tolerance annotations from IR policy metadata`() {
    listOf(
      GenerationMode.Client to "value = 20",
      GenerationMode.Server to "value = 100",
    ).forEach { (mode, rateLimitValue) ->
      val typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          mode,
          setOf(),
          problemLibrary = KotlinProblemLibrary.QUARKUS,
          problemRfc = KotlinProblemRfc.RFC9457,
        )

      KotlinJAXRSIrGenerator(
        policyApi(),
        typeRegistry,
        testOptions(quarkus = true),
      ).generateServiceTypes()

      val builtTypes = typeRegistry.buildTypes()
      val source = kotlinSource("io.test.service", findType("io.test.service.GuardedAPI", builtTypes))

      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
      assertTrue(source.contains("import io.smallrye.faulttolerance.api.RateLimit"), source)
      assertTrue(source.contains("import org.eclipse.microprofile.faulttolerance.CircuitBreaker"), source)
      assertTrue(source.contains("import org.eclipse.microprofile.faulttolerance.Retry"), source)
      assertTrue(source.contains("import org.eclipse.microprofile.faulttolerance.Timeout"), source)
      assertTrue(source.contains("import java.time.temporal.ChronoUnit"), source)
      assertTrue(source.contains("@Timeout("), source)
      assertTrue(source.contains("value = 5"), source)
      assertTrue(source.contains("unit = ChronoUnit.SECONDS"), source)
      assertTrue(source.contains("maxRetries = 3"), source)
      assertTrue(source.contains("delay = 1"), source)
      assertTrue(source.contains("delayUnit = ChronoUnit.SECONDS"), source)
      assertTrue(source.contains("jitter = 100"), source)
      assertTrue(source.contains("jitterDelayUnit = ChronoUnit.MILLIS"), source)
      assertTrue(source.contains("requestVolumeThreshold = 10"), source)
      assertTrue(source.contains("failureRatio = 0.75"), source)
      assertTrue(source.contains("window = 1"), source)
      assertTrue(source.contains("windowUnit = ChronoUnit.MINUTES"), source)
      assertTrue(source.contains(rateLimitValue), source)
    }
  }

  @Test
  fun `rejects unsupported Quarkus fault tolerance policy metadata`() {
    listOf(
      GeneratedPolicy(
        retry = mapOf("maximumRetries" to "3"),
      ) to "Unsupported Quarkus retry policy key(s): maximumRetries",
      GeneratedPolicy(circuitBreaker = mapOf("requestVolume" to "10")) to
        "Unsupported Quarkus circuitBreaker policy key(s): requestVolume",
      GeneratedPolicy(serverRateLimit = mapOf("window" to "PT1M")) to
        "Quarkus rateLimit policy requires integer key 'value'",
      GeneratedPolicy(timeout = "100") to
        "Quarkus timeout policy key 'value' must be an ISO-8601 duration " +
        "(e.g. \"PT5S\") or a PT{n}MS milliseconds literal (e.g. \"PT100MS\")",
      GeneratedPolicy(retry = mapOf("delay" to "100")) to
        "Quarkus retry policy key 'delay' must be an ISO-8601 duration " +
        "(e.g. \"PT5S\") or a PT{n}MS milliseconds literal (e.g. \"PT100MS\")",
    ).forEach { (policy, expectedMessage) ->
      val typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Server,
          setOf(),
          problemLibrary = KotlinProblemLibrary.QUARKUS,
          problemRfc = KotlinProblemRfc.RFC9457,
        )

      val error =
        assertThrows(GenerationException::class.java) {
          KotlinJAXRSIrGenerator(
            policyApi(policy),
            typeRegistry,
            testOptions(quarkus = true),
          ).generateServiceTypes()
        }

      assertTrue(error.message?.contains(expectedMessage) == true, error.message)
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus Zanzibar annotations from IR auth metadata in server mode`() {
    val serverRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )

    KotlinJAXRSIrGenerator(
      zanzibarApi(),
      serverRegistry,
      testOptions(quarkus = true),
    ).generateServiceTypes()

    val builtTypes = serverRegistry.buildTypes()
    val source = kotlinSource("io.test.service", findType("io.test.service.ProjectsAPI", builtTypes))
    val userExtractorSource =
      kotlinSource(
        "io.test.service",
        findType("io.test.service.ZanzibarJwtUserExtractor", builtTypes),
      )

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGAHeaderObject"), source)
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGAIgnore"), source)
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGAObject"), source)
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGAPathObject"), source)
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGAQueryObject"), source)
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGARelation"), source)
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGARequestObject"), source)
    assertTrue(source.contains("import io.quarkiverse.zanzibar.annotations.FGAUserType"), source)
    assertTrue(source.contains("@FGAPathObject("), source)
    assertTrue(source.contains("param = \"projectId\""), source)
    assertTrue(source.contains("type = \"project\""), source)
    assertTrue(source.contains("@FGARelation(\"can_read\")"), source)
    assertTrue(source.contains("@FGAUserType(\"user\")"), source)
    assertTrue(source.contains("@FGAQueryObject("), source)
    assertTrue(source.contains("param = \"teamId\""), source)
    assertTrue(source.contains("type = \"team\""), source)
    assertTrue(source.contains("@FGAHeaderObject("), source)
    assertTrue(source.contains("name = \"X-Account-Id\""), source)
    assertTrue(source.contains("type = \"account\""), source)
    assertTrue(source.contains("@FGARequestObject("), source)
    assertTrue(source.contains("property = \"requestProjectId\""), source)
    assertTrue(source.contains("@FGAObject("), source)
    assertTrue(source.contains("id = \"public\""), source)
    assertTrue(source.contains("type = \"workspace\""), source)
    assertTrue(source.contains("@FGARelation(FGARelation.ANY)"), source)
    assertTrue(source.contains("@FGAIgnore"), source)
    assertTrue(userExtractorSource.contains("import io.quarkiverse.zanzibar.UserExtractor"), userExtractorSource)
    assertTrue(userExtractorSource.contains("import jakarta.enterprise.context.ApplicationScoped"), userExtractorSource)
    assertTrue(userExtractorSource.contains("import org.eclipse.microprofile.jwt.JsonWebToken"), userExtractorSource)
    assertTrue(userExtractorSource.contains("@ApplicationScoped"), userExtractorSource)
    assertTrue(userExtractorSource.contains("public class ZanzibarJwtUserExtractor"), userExtractorSource)
    assertTrue(userExtractorSource.contains("jwt.getClaim<String>(\"azp\")"), userExtractorSource)
    assertTrue(userExtractorSource.contains("jwt.subject"), userExtractorSource)
    assertFalse(userExtractorSource.contains("principal.name"), userExtractorSource)
  }

  @Test
  fun `rejects Zanzibar object type without object id source`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )

    val error =
      assertThrows(GenerationException::class.java) {
        KotlinJAXRSIrGenerator(
          zanzibarApi(
            operationZanzibar =
              mapOf(
                "resourceType" to "project",
                "permission" to "can_read",
              ),
          ),
          typeRegistry,
          testOptions(quarkus = true),
        ).generateServiceTypes()
      }

    assertTrue(
      error.message?.contains(
        "Zanzibar object type 'project' requires one of objectId, pathParam, queryParam, header, or requestProperty",
      ) == true,
      error.message,
    )
  }

  @Test
  fun `reports conflicting Zanzibar user source metadata`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )
    val api = zanzibarApi()
    val conflictingApi =
      api.copy(
        services =
          listOf(
            api.services.single().copy(
              auth =
                GeneratedAuth(
                  zanzibarUserSource =
                    GeneratedZanzibarUserSource(
                      jwt = GeneratedZanzibarJwtUserSource(claims = listOf("email")),
                    ),
                ),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        KotlinJAXRSIrGenerator(
          conflictingApi,
          typeRegistry,
          testOptions(quarkus = true),
        ).generateServiceTypes()
      }

    assertTrue(
      error.message?.contains(
        "Cannot generate more than one Zanzibar user source: " +
          "jwt(claims=[azp, sub], principalFallback=false); jwt(claims=[email], principalFallback=false)",
      ) == true,
      error.message,
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates strict opt-in principal fallback for Quarkus Zanzibar JWT user extractors`() {
    val serverRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )

    KotlinJAXRSIrGenerator(
      zanzibarApi(principalFallback = true),
      serverRegistry,
      testOptions(quarkus = true),
    ).generateServiceTypes()

    val builtTypes = serverRegistry.buildTypes()
    val userExtractorSource =
      kotlinSource(
        "io.test.service",
        findType("io.test.service.ZanzibarJwtUserExtractor", builtTypes),
      )

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(userExtractorSource.contains(" ?: principal.name.takeIf { it.isNotBlank() }"), userExtractorSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates principal fallback directly for empty Quarkus Zanzibar JWT claim lists`() {
    val serverRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )
    val api =
      zanzibarApi().copy(
        auth =
          GeneratedAuth(
            zanzibarUserSource =
              GeneratedZanzibarUserSource(
                jwt =
                  GeneratedZanzibarJwtUserSource(
                    claims = emptyList(),
                    principalFallback = true,
                  ),
              ),
          ),
      )

    KotlinJAXRSIrGenerator(
      api,
      serverRegistry,
      testOptions(quarkus = true),
    ).generateServiceTypes()

    val builtTypes = serverRegistry.buildTypes()
    val userExtractorSource =
      kotlinSource(
        "io.test.service",
        findType("io.test.service.ZanzibarJwtUserExtractor", builtTypes),
      )

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(
      userExtractorSource.contains("val userId = principal.name.takeIf { it.isNotBlank() }"),
      userExtractorSource,
    )
    assertFalse(userExtractorSource.contains("null ?: principal.name"), userExtractorSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `does not generate Quarkus Zanzibar annotations in client mode`() {
    val clientRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = KotlinProblemRfc.RFC9457,
      )

    KotlinJAXRSIrGenerator(
      zanzibarApi(),
      clientRegistry,
      testOptions(quarkus = true),
    ).generateServiceTypes()

    val builtTypes = clientRegistry.buildTypes()
    val source = kotlinSource("io.test.service", findType("io.test.service.ProjectsAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertFalse(source.contains("FGA"), source)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates aggregate JAX-RS subresources from IR with client and server modes`() {
    listOf(GenerationMode.Client, GenerationMode.Server).forEach { mode ->
      listOf(false, true).forEach { quarkus ->
        val typeRegistry =
          KotlinTypeRegistry(
            "io.test",
            null,
            mode,
            setOf(),
            problemLibrary = KotlinProblemLibrary.ZALANDO,
            problemRfc = KotlinProblemRfc.RFC7807,
          )

        KotlinJAXRSIrGenerator(
          aggregateServicesApi(),
          typeRegistry,
          testOptions(
            quarkus = quarkus,
            aggregateServices = true,
            aggregateServiceName = "TurnPostAPI",
          ),
        ).generateServiceTypes()

        val builtTypes = typeRegistry.buildTypes()
        val aggregateSource = kotlinSource("io.test.service", findType("io.test.service.TurnPostAPI", builtTypes))
        val usersSource = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))
        val projectsSource = kotlinSource("io.test.service", findType("io.test.service.ProjectsAPI", builtTypes))
        val utilitySource = kotlinSource("io.test.service", findType("io.test.service.UtilityAPI", builtTypes))

        assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
        assertTrue(aggregateSource.contains("public interface TurnPostAPI"), aggregateSource)
        assertTrue(aggregateSource.contains("@Path(value = \"/\")"), aggregateSource)
        assertTrue(aggregateSource.contains("@Path(value = \"/users\")"), aggregateSource)
        assertTrue(aggregateSource.contains("public fun users(): UsersAPI"), aggregateSource)
        assertTrue(aggregateSource.contains("@Path(value = \"/projects/{projectId}\")"), aggregateSource)
        assertTrue(aggregateSource.contains("public fun projects("), aggregateSource)
        assertTrue(aggregateSource.contains("projectId: String): ProjectsAPI"), aggregateSource)
        assertTrue(aggregateSource.contains("public fun utility(): UtilityAPI"), aggregateSource)

        assertTrue(usersSource.contains("public fun createUser()"), usersSource)
        assertFalse(usersSource.contains("@Path(value = \"/\")"), usersSource)
        assertFalse(usersSource.contains("@Path(value = \"\")"), usersSource)
        assertFalse(usersSource.contains("@Path(value = \"/users\")"), usersSource)
        assertFalse(usersSource.contains("@Path(value = \"/users/me\")"), usersSource)
        assertTrue(usersSource.contains("@Path(value = \"/me\")"), usersSource)
        assertTrue(usersSource.contains("@Path(value = \"/{userId}\")"), usersSource)

        assertFalse(projectsSource.contains("@Path(value = \"/projects/{projectId}/repos/{repoId}\")"), projectsSource)
        assertTrue(projectsSource.contains("@Path(value = \"/repos/{repoId}\")"), projectsSource)
        assertTrue(projectsSource.contains("repoId: String"), projectsSource)
        assertFalse(projectsSource.contains("projectId:"), projectsSource)

        assertTrue(utilitySource.contains("@Path(value = \"/health\")"), utilitySource)
        assertTrue(utilitySource.contains("@Path(value = \"/status\")"), utilitySource)
      }
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus aggregate REST client registration without URL path`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    KotlinJAXRSIrGenerator(
      aggregateServicesApi(baseUri = "http://localhost:9080"),
      typeRegistry,
      testOptions(
        quarkus = true,
        aggregateServices = true,
        aggregateServiceName = "TurnPostAPI",
      ),
    ).generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val aggregateSource = kotlinSource("io.test.service", findType("io.test.service.TurnPostAPI", builtTypes))
    val usersSource = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(aggregateSource.contains("@RegisterRestClient(baseUri = \"http://localhost:9080\")"), aggregateSource)
    assertFalse(aggregateSource.contains("@Path(value = \"http://localhost:9080\")"), aggregateSource)
    assertFalse(usersSource.contains("@RegisterRestClient"), usersSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus REST client metadata on non-aggregate services`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    KotlinJAXRSIrGenerator(
      aggregateServicesApi(
        baseUri = "http://localhost:9080",
        apiRestClient = GeneratedJaxrsRestClient(configKey = "platform"),
        serviceRestClient =
          GeneratedJaxrsRestClient(
            configKey = "graphs",
            oidcClient = "graphs",
            providers = listOf("io.test.client.GraphsClientFilter"),
          ),
      ),
      typeRegistry,
      testOptions(quarkus = true),
    ).generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val usersSource = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(usersSource.contains("@RegisterRestClient("), usersSource)
    assertTrue(usersSource.contains("configKey = \"graphs\""), usersSource)
    assertTrue(usersSource.contains("baseUri = \"http://localhost:9080\""), usersSource)
    assertTrue(usersSource.contains("@OidcClientFilter(\"graphs\")"), usersSource)
    assertTrue(usersSource.contains("@RegisterProvider(GraphsClientFilter::class)"), usersSource)
  }

  @Test
  fun `reports invalid Quarkus REST client provider class names`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val error =
      assertThrows(GenerationException::class.java) {
        KotlinJAXRSIrGenerator(
          aggregateServicesApi(
            serviceRestClient = GeneratedJaxrsRestClient(providers = listOf("not-a-class")),
          ),
          typeRegistry,
          testOptions(quarkus = true),
        ).generateServiceTypes()
      }

    assertTrue(
      error.message?.contains("Invalid provider class name 'not-a-class' in REST client metadata") == true,
      error.message,
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates aggregate Quarkus REST client metadata from root only`() {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    KotlinJAXRSIrGenerator(
      aggregateServicesApi(
        baseUri = "http://localhost:9080",
        apiRestClient =
          GeneratedJaxrsRestClient(
            configKey = "platform",
            oidcClient = "public",
            providers = listOf("io.test.client.GraphsClientFilter"),
          ),
        serviceRestClient = GeneratedJaxrsRestClient(configKey = "graphs", oidcClient = "graphs"),
      ),
      typeRegistry,
      testOptions(
        quarkus = true,
        aggregateServices = true,
        aggregateServiceName = "TurnPostAPI",
      ),
    ).generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val aggregateSource = kotlinSource("io.test.service", findType("io.test.service.TurnPostAPI", builtTypes))
    val usersSource = kotlinSource("io.test.service", findType("io.test.service.UsersAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(aggregateSource.contains("@RegisterRestClient("), aggregateSource)
    assertTrue(aggregateSource.contains("configKey = \"platform\""), aggregateSource)
    assertTrue(aggregateSource.contains("baseUri = \"http://localhost:9080\""), aggregateSource)
    assertTrue(aggregateSource.contains("@OidcClientFilter(\"public\")"), aggregateSource)
    assertTrue(aggregateSource.contains("@RegisterProvider(GraphsClientFilter::class)"), aggregateSource)
    assertFalse(usersSource.contains("@RegisterRestClient"), usersSource)
    assertFalse(usersSource.contains("@OidcClientFilter"), usersSource)
    assertFalse(usersSource.contains("@RegisterProvider"), usersSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `treats OpenAPI empty schemas as Any in Kotlin JAX-RS`(
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = clientTypeRegistry()
    val api = GeneratedApiIrExporter().export(testUri)

    KotlinJAXRSIrGenerator(api, typeRegistry, kotlinJAXRSTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val holderSource = kotlinSource("io.test", findType("io.test.AnyHolder", builtTypes))
    val serviceSource = kotlinSource("io.test.service", findType("io.test.service.API", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(holderSource.contains("public val `value`: Any?"), holderSource)
    assertTrue(holderSource.contains("public val `documented`: Any?"), holderSource)
    assertTrue(holderSource.contains("public val `named`: Any?"), holderSource)
    assertTrue(serviceSource.contains("body: Any"), serviceSource)
    assertTrue(serviceSource.contains("public fun updateValue("), serviceSource)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `uses OpenAPI enum varnames and wire values in Kotlin JAX-RS`(
    @ResourceUri("openapi/ir/enum-varnames-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api = GeneratedApiIrExporter().export(testUri)

    KotlinJAXRSIrGenerator(api, typeRegistry, kotlinJAXRSTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val notificationTypeSource = kotlinSource("io.test", findType("io.test.NotificationType", builtTypes))
    val fallbackTypeSource = kotlinSource("io.test", findType("io.test.FallbackType", builtTypes))
    val notificationSource = kotlinSource("io.test", findType("io.test.Notification", builtTypes))
    val eventSource = kotlinSource("io.test", findType("io.test.NotificationEvent", builtTypes))
    val activitySource = kotlinSource("io.test", findType("io.test.NotificationActivity", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(
      notificationTypeSource.contains("PullRequestReviewRequested(\"notification.pull_request.review_requested\")"),
      notificationTypeSource,
    )
    assertTrue(notificationTypeSource.contains("PullRequestMerged(\"notification.pull_request.merged\")"))
    assertTrue(notificationTypeSource.contains("TeamMemberAdded(\"notification.team.member_added\")"))
    assertTrue(notificationTypeSource.contains("@JsonValue"), notificationTypeSource)
    assertTrue(notificationTypeSource.contains("@JsonCreator"), notificationTypeSource)
    assertTrue(fallbackTypeSource.contains("OPEN(\"OPEN\")"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("LowerSnake(\"lower_snake\")"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("UpperInterCaps(\"UpperInterCaps\")"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("LowerInterCaps(\"lowerInterCaps\")"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("DottedCase(\"dotted.case\")"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("MixedKebabCase(\"mixed-kebab.case\")"), fallbackTypeSource)
    assertTrue(notificationSource.contains("public val `type`: NotificationType"), notificationSource)
    assertTrue(eventSource.contains("public val `kind`: NotificationType"), eventSource)
    assertTrue(
      activitySource.contains("if (discriminatorValue == \"notification.pull_request.review_requested\")"),
      activitySource,
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates aggregate Quarkus client locators for AsyncAPI channel path parameters`(
    @ResourceUri("asyncapi/ir/channel-parameters.yaml") testUri: URI,
  ) {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)
    val api =
      fragment.api.copy(
        services =
          fragment.api.services +
            GeneratedService(
              name = "HealthService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "health",
                    method = "GET",
                    path = "/health",
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
      )

    KotlinJAXRSIrGenerator(
      api,
      typeRegistry,
      testOptions(
        quarkus = true,
        aggregateServices = true,
        aggregateServiceName = "TurnPostAPI",
      ),
    ).generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val aggregateSource = kotlinSource("io.test.service", findType("io.test.service.TurnPostAPI", builtTypes))
    val serviceSource = kotlinSource("io.test.service", findType("io.test.service.NarrativeIqAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(aggregateSource.contains("@Path(value = \"/niq/v0/repos/{repoId}/events\")"), aggregateSource)
    assertTrue(
      aggregateSource.contains("public fun narrativeIq(@RestPath repoId: String): NarrativeIqAPI"),
      aggregateSource,
    )
    assertTrue(
      serviceSource.contains("public fun streamNarrativeIqRepoEvents(): Multi<NarrativeChange>"),
      serviceSource,
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates explicit security parameters from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-explicit-security-param.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf()),
      options =
        KotlinJAXRSOptions(
          coroutineFlowMethods = false,
          coroutineServiceMethods = false,
          reactiveResponseType = null,
          explicitSecurityParameters = true,
          baseUriMode = null,
          alwaysUseResponseReturn = false,
          defaultServicePackageName = "io.test.service",
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
        ),
      filePackageName = "io.test.service",
      snapshotPath = "RequestExplicitSecurityParamsTest/test-explicit-security-parameter-generation.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates explicit request body media from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf()),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "RequestBodyParamTest/test-generation-of-body-parameter-with-explicit-content-type.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates explicit response media from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf()),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath =
        "ResponseBodyContentTest/test-generation-of-body-parameter-with-explicit-content-type-in-client-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates JSON body override from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-body-param-json-override.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf()),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "RequestBodyParamTest/test-body-parameter-generation-with-json-override-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates request body validation from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ValidationConstraints)),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "RequestBodyParamTest/test-basic-body-parameter-generation-with-validation-constraints.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates response wrapper in client mode when IR response headers are declared`(
    @ResourceUri("raml/resource-gen/res-headers.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf()),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "ResponseHeadersTest/test-response-headers-force-response-return-in-client-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates rest response wrapper in quarkus client mode when IR response headers are declared`(
    @ResourceUri("raml/resource-gen/res-headers.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf()),
      options =
        KotlinJAXRSOptions(
          coroutineFlowMethods = false,
          coroutineServiceMethods = false,
          reactiveResponseType = null,
          explicitSecurityParameters = false,
          baseUriMode = null,
          alwaysUseResponseReturn = false,
          defaultServicePackageName = "io.test.service",
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = true,
        ),
      filePackageName = "io.test.service",
      snapshotPath =
        "ResponseHeadersTest/test-response-headers-force-rest-response-return-in-quarkus-client-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates problem registration from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Server,
          setOf(JacksonAnnotations),
          problemLibrary = KotlinProblemLibrary.ZALANDO,
          problemRfc = KotlinProblemRfc.RFC7807,
        ),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "ResponseProblemsTest/test-api-problem-registration-in-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates referenced problem types from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      typeName = "io.test.InvalidIdProblem",
      snapshotPath = "ResponseProblemsTest/test-problem-type-generation-in-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates nullify methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry =
        KotlinTypeRegistry(
          "io.test",
          null,
          GenerationMode.Client,
          setOf(),
          problemLibrary = KotlinProblemLibrary.ZALANDO,
          problemRfc = KotlinProblemRfc.RFC7807,
        ),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test",
      snapshotPath = "RequestMethodsTest/test-request-method-generation-in-client-mode-with-nullify.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates coroutine service methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options =
        testOptions(
          coroutineServiceMethods = true,
        ),
      filePackageName = "io.test.service",
      snapshotPath = "RequestCoroutineMethodsTest/test-basic-coroutines-method-generation-in-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates reactive service methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options =
        testOptions(
          reactiveResponseType = CompletionStage::class.qualifiedName,
        ),
      filePackageName = "io.test.service",
      snapshotPath = "RequestReactiveMethodsTest/test-basic-reactive-method-generation-in-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates asynchronous service methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-async.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "ResponseAsyncTest/test-basic-body-parameter-generation-in-async-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates server sent event methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "ResponseSseTest/test-basic-sse-method-generation-in-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates coroutine event stream methods from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options =
        testOptions(
          coroutineFlowMethods = false,
          coroutineServiceMethods = true,
        ),
      filePackageName = "io.test.service",
      snapshotPath = "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates client base URI path from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = clientTypeRegistry(),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "BaseUriTest/test-baseurl-generation-in-api-client-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates server base URI path from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "BaseUriTest/test-baseurl-generation-in-api-server-mode.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates JAX-RS context parameters from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-jaxrs-context.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options = kotlinJAXRSTestOptions,
      filePackageName = "io.test.service",
      snapshotPath = "RequestContextParamTest/test-jaxrscontext-annotation-adds-context-parameters.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates OpenAPI server-only Quarkus context parameters without excluded body parameter`(
    @ResourceUri("openapi/ir/jaxrs-exclusions-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(UseJakartaPackages),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      OpenApiToGeneratedApi(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .convert(testUri)

    KotlinJAXRSIrGenerator(api, typeRegistry, testOptions(quarkus = true))
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source = kotlinSource("io.test.service", findType("io.test.service.ImportsAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes + routingContextStub()))
    assertTrue(source.contains("import io.vertx.ext.web.RoutingContext"), source)
    assertTrue(source.contains("@Context routingContext: RoutingContext"), source)
    assertTrue(source.contains("reset: Boolean?"), source)
    assertTrue(source.contains("xImportId: String?"), source)
    assertFalse(source.contains("body:"), source)
    assertFalse(source.contains("trace:"), source)
    assertFalse(source.contains("xDebug"), source)
    assertFalse(source.contains("X-Debug"), source)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates OpenAPI client-only JAX-RS context parameters with client request body`(
    @ResourceUri("openapi/ir/jaxrs-exclusions-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(UseJakartaPackages),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
      )
    val api =
      OpenApiToGeneratedApi(GeneratedApiIrOptions(generationMode = GenerationMode.Client))
        .convert(testUri)

    KotlinJAXRSIrGenerator(api, typeRegistry, testOptions())
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source = kotlinSource("io.test.service", findType("io.test.service.ImportsAPI", builtTypes))

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertTrue(source.contains("import jakarta.ws.rs.core.HttpHeaders"), source)
    assertTrue(source.contains("@Context headers: HttpHeaders"), source)
    assertTrue(source.contains("trace: String?"), source)
    assertTrue(source.contains("body: ByteArray"), source)
    assertFalse(source.contains("routingContext"), source)
    assertFalse(source.contains("xDebug"), source)
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus SSE annotations from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = typeRegistry(),
      options = testOptions(quarkus = true),
      filePackageName = "io.test.service",
      snapshotPath =
        "ResponseSseTest/test-basic-sse-method-generation-in-server-mode-with-quarkus-option-enabled.output.kt",
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `generates Quarkus explicit security parameters from IR with existing Kotlin JAX-RS output shape`(
    @ResourceUri("raml/resource-gen/req-explicit-security-param.raml") testUri: URI,
  ) {
    assertIrSnapshot(
      testUri = testUri,
      typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf()),
      options =
        testOptions(
          explicitSecurityParameters = true,
          quarkus = true,
        ),
      filePackageName = "io.test.service",
      snapshotPath =
        "RequestExplicitSecurityParamsTest/test-explicit-security-parameter-generation-with-quarkus-option-enabled.output.kt",
    )
  }

  private fun testOptions(
    coroutineFlowMethods: Boolean = false,
    coroutineServiceMethods: Boolean = false,
    reactiveResponseType: String? = null,
    explicitSecurityParameters: Boolean = false,
    baseUriMode: KotlinJAXRSOptions.BaseUriMode? = null,
    alwaysUseResponseReturn: Boolean = false,
    quarkus: Boolean = false,
    aggregateServices: Boolean = false,
    aggregateServiceName: String? = null,
  ): KotlinJAXRSOptions =
    KotlinJAXRSOptions(
      coroutineFlowMethods = coroutineFlowMethods,
      coroutineServiceMethods = coroutineServiceMethods,
      reactiveResponseType = reactiveResponseType,
      explicitSecurityParameters = explicitSecurityParameters,
      baseUriMode = baseUriMode,
      alwaysUseResponseReturn = alwaysUseResponseReturn,
      defaultServicePackageName = "io.test.service",
      defaultProblemBaseUri = "http://example.com/",
      defaultMediaTypes = listOf("application/json"),
      serviceSuffix = "API",
      quarkus = quarkus,
      aggregateServices = aggregateServices,
      aggregateServiceName = aggregateServiceName,
    )

  private fun clientModelApi(): GeneratedApi =
    GeneratedApi(
      name = "Profiles API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.RAML, location = "memory://profiles"),
      models =
        listOf(
          GeneratedModel(
            name = "BaseProfile",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty(
                  name = "id",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
              ),
          ),
          GeneratedModel(
            name = "UserProfile",
            kind = GeneratedModel.Kind.OBJECT,
            inherits = listOf(GeneratedTypeRef.named("BaseProfile")),
            properties =
              listOf(
                GeneratedModelProperty(
                  name = "id",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
                GeneratedModelProperty(
                  name = "displayName",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
              ),
          ),
        ),
    )

  private fun policyApi(
    policy: GeneratedPolicy =
      GeneratedPolicy(
        timeout = "PT5S",
        retry =
          mapOf(
            "maxRetries" to "3",
            "delay" to "PT1S",
            "jitter" to "PT100MS",
          ),
        circuitBreaker =
          mapOf(
            "requestVolumeThreshold" to "10",
            "failureRatio" to "0.75",
            "delay" to "PT30S",
          ),
        clientRateLimit =
          mapOf(
            "value" to "20",
            "window" to "PT1M",
          ),
        serverRateLimit =
          mapOf(
            "value" to "100",
            "window" to "PT1M",
          ),
      ),
  ): GeneratedApi =
    GeneratedApi(
      name = "Guarded API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://guarded"),
      services =
        listOf(
          GeneratedService(
            name = "Guarded",
            operations =
              listOf(
                GeneratedOperation(
                  id = "guarded",
                  method = "GET",
                  path = "/guarded",
                  responses = listOf(GeneratedResponse(status = 204)),
                  policy = policy,
                ),
              ),
          ),
        ),
    )

  private fun zanzibarApi(
    principalFallback: Boolean = false,
    operationZanzibar: Map<String, String> =
      mapOf(
        "resourceType" to "project",
        "pathParam" to "projectId",
        "permission" to "can_read",
        "userType" to "user",
      ),
  ): GeneratedApi =
    GeneratedApi(
      name = "Projects API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://projects"),
      auth =
        GeneratedAuth(
          zanzibarUserSource =
            GeneratedZanzibarUserSource(
              jwt =
                GeneratedZanzibarJwtUserSource(
                  claims = listOf("azp", "sub"),
                  principalFallback = principalFallback,
                ),
            ),
        ),
      services =
        listOf(
          GeneratedService(
            name = "Projects",
            operations =
              listOf(
                GeneratedOperation(
                  id = "getProject",
                  method = "GET",
                  path = "/projects/{projectId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "projectId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses = listOf(GeneratedResponse(status = 204)),
                  auth =
                    GeneratedAuth(
                      zanzibar = operationZanzibar,
                    ),
                ),
                GeneratedOperation(
                  id = "getTeam",
                  method = "GET",
                  path = "/teams",
                  responses = listOf(GeneratedResponse(status = 204)),
                  auth =
                    GeneratedAuth(
                      zanzibar =
                        mapOf(
                          "objectType" to "team",
                          "queryParam" to "teamId",
                          "relation" to "member",
                        ),
                    ),
                ),
                GeneratedOperation(
                  id = "getAccount",
                  method = "GET",
                  path = "/account",
                  responses = listOf(GeneratedResponse(status = 204)),
                  auth =
                    GeneratedAuth(
                      zanzibar =
                        mapOf(
                          "resourceType" to "account",
                          "header" to "X-Account-Id",
                          "permission" to "viewer",
                        ),
                    ),
                ),
                GeneratedOperation(
                  id = "getRequestProject",
                  method = "GET",
                  path = "/request-project",
                  responses = listOf(GeneratedResponse(status = 204)),
                  auth =
                    GeneratedAuth(
                      zanzibar =
                        mapOf(
                          "resourceType" to "project",
                          "requestProperty" to "requestProjectId",
                          "permission" to "viewer",
                        ),
                    ),
                ),
                GeneratedOperation(
                  id = "getPublicWorkspace",
                  method = "GET",
                  path = "/public-workspace",
                  responses = listOf(GeneratedResponse(status = 204)),
                  auth =
                    GeneratedAuth(
                      zanzibar =
                        mapOf(
                          "resourceType" to "workspace",
                          "resourceId" to "public",
                          "permission" to "*",
                        ),
                    ),
                ),
                GeneratedOperation(
                  id = "ignored",
                  method = "GET",
                  path = "/ignored",
                  responses = listOf(GeneratedResponse(status = 204)),
                  auth = GeneratedAuth(zanzibar = mapOf("ignore" to "true")),
                ),
              ),
          ),
        ),
    )

  private fun contentTypeApi(): GeneratedApi =
    GeneratedApi(
      name = "Users API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://users"),
      services =
        listOf(
          GeneratedService(
            name = "UsersService",
            group = "Users",
            operations =
              listOf(
                GeneratedOperation(
                  id = "putUserAvatar",
                  method = "PUT",
                  path = "/users/{userId}/avatar",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                      GeneratedParameter(
                        name = "contentType",
                        location = GeneratedParameter.Location.HEADER,
                        type = GeneratedTypeRef.named("AvatarContentType"),
                        required = true,
                        serializationName = "Content-Type",
                      ),
                    ),
                  requestBody =
                    GeneratedPayload(
                      type = GeneratedTypeRef.scalar("file"),
                      mediaTypes = listOf("image/png", "image/jpeg", "image/webp"),
                    ),
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
              ),
          ),
        ),
      models =
        listOf(
          GeneratedModel(
            name = "AvatarContentType",
            kind = GeneratedModel.Kind.ENUM,
            values = listOf("image/png", "image/jpeg", "image/webp"),
          ),
        ),
    )

  private fun validationModelApi(): GeneratedApi =
    GeneratedApi(
      name = "Validation API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://validation"),
      services =
        listOf(
          GeneratedService(
            name = "UsersService",
            operations =
              listOf(
                GeneratedOperation(
                  id = "createUser",
                  method = "POST",
                  path = "/users",
                  requestBody =
                    GeneratedPayload(
                      type = GeneratedTypeRef.named("CreateUserRequest"),
                      mediaTypes = listOf("application/json"),
                    ),
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
              ),
          ),
        ),
      models =
        listOf(
          GeneratedModel(
            name = "CreateUserRequest",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty(
                  "email",
                  GeneratedTypeRef.scalar("string", format = "email"),
                  required = true,
                ),
                GeneratedModelProperty(
                  "password",
                  GeneratedTypeRef.scalar("string"),
                  required = true,
                  validation = mapOf("minLength" to "8"),
                ),
                GeneratedModelProperty(
                  "displayName",
                  GeneratedTypeRef.scalar("string"),
                  required = true,
                  validation = mapOf("pattern" to "^[A-Za-z0-9_]+$"),
                ),
              ),
          ),
        ),
    )

  private fun unionAndEventsApi(): GeneratedApi =
    GeneratedApi(
      name = "Union API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://union"),
      services =
        listOf(
          GeneratedService(
            name = "UsersService",
            group = "Users",
            operations =
              listOf(
                GeneratedOperation(
                  id = "getUser",
                  method = "GET",
                  path = "/users/me",
                  responses =
                    listOf(
                      GeneratedResponse(
                        status = 200,
                        type = GeneratedTypeRef.named("UserResponse"),
                        mediaTypes = listOf("application/json"),
                      ),
                    ),
                ),
                GeneratedOperation(
                  id = "putPullRequestReviewer",
                  method = "PUT",
                  path = "/users/me/reviewer",
                  requestBody =
                    GeneratedPayload(
                      type = GeneratedTypeRef.named("AssignPullRequestReviewerRequest"),
                      mediaTypes = listOf("application/json"),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(
                        status = 200,
                        type = GeneratedTypeRef.named("UserSelfResponse"),
                        mediaTypes = listOf("application/json"),
                      ),
                    ),
                ),
              ),
          ),
        ),
      models =
        listOf(
          GeneratedModel(
            name = "UserSelfResponse",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty(
                  "createdAt",
                  GeneratedTypeRef.scalar("string", format = "date-time"),
                  required = true,
                ),
              ),
          ),
          GeneratedModel(
            name = "UserSummaryResponse",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
          GeneratedModel(
            name = "UserResponse",
            kind = GeneratedModel.Kind.UNION,
            aliases = listOf(GeneratedTypeRef.named("UserSelfResponse"), GeneratedTypeRef.named("UserSummaryResponse")),
          ),
          GeneratedModel(
            name = "AssignPullRequestReviewerRequest",
            kind = GeneratedModel.Kind.OBJECT,
            additionalProperties = GeneratedAdditionalProperties(allowed = true),
          ),
          GeneratedModel(
            name = "EventEnvelope",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty(
                  name = "data",
                  type = GeneratedTypeRef.named("EventData"),
                  required = true,
                  externalDiscriminator = "type",
                ),
              ),
          ),
          GeneratedModel(
            name = "EventData",
            kind = GeneratedModel.Kind.OBJECT,
            discriminatorMappings =
              mapOf(
                "accounts.team.created" to GeneratedTypeRef.named("AccountsTeamCreatedData"),
              ),
          ),
          GeneratedModel(
            name = "AccountsTeamCreatedData",
            kind = GeneratedModel.Kind.OBJECT,
            inherits = listOf(GeneratedTypeRef.named("EventData")),
            properties =
              listOf(
                GeneratedModelProperty("teamId", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
        ),
    )

  private fun formatApi(): GeneratedApi =
    GeneratedApi(
      name = "Format API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://format"),
      models =
        listOf(
          GeneratedModel(
            name = "FormatModel",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty(
                  "createdAt",
                  GeneratedTypeRef.scalar("string", format = "date-time"),
                  required = true,
                ),
                GeneratedModelProperty(
                  "localCreatedAt",
                  GeneratedTypeRef.scalar("string", format = "date-time-only"),
                  required = true,
                ),
                GeneratedModelProperty("birthday", GeneratedTypeRef.scalar("string", format = "date"), required = true),
                GeneratedModelProperty("startsAt", GeneratedTypeRef.scalar("string", format = "time"), required = true),
                GeneratedModelProperty("targetUrl", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                GeneratedModelProperty("id", GeneratedTypeRef.scalar("string", format = "uuid"), required = true),
                GeneratedModelProperty("anyValue", GeneratedTypeRef.scalar("any"), required = true),
                GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("enabled", GeneratedTypeRef.scalar("boolean"), required = true),
                GeneratedModelProperty("defaultCount", GeneratedTypeRef.scalar("integer"), required = true),
                GeneratedModelProperty(
                  "smallCount",
                  GeneratedTypeRef.scalar("integer", format = "int8"),
                  required = true,
                ),
                GeneratedModelProperty(
                  "mediumCount",
                  GeneratedTypeRef.scalar("integer", format = "int16"),
                  required = true,
                ),
                GeneratedModelProperty("count", GeneratedTypeRef.scalar("integer", format = "int32"), required = true),
                GeneratedModelProperty(
                  "aliasCount",
                  GeneratedTypeRef.scalar("integer", format = "int"),
                  required = true,
                ),
                GeneratedModelProperty(
                  "largeCount",
                  GeneratedTypeRef.scalar("integer", format = "int64"),
                  required = true,
                ),
                GeneratedModelProperty(
                  "aliasLargeCount",
                  GeneratedTypeRef.scalar("integer", format = "long"),
                  required = true,
                ),
                GeneratedModelProperty("legacyCount", GeneratedTypeRef.scalar("int32"), required = true),
                GeneratedModelProperty("legacyLargeCount", GeneratedTypeRef.scalar("int64"), required = true),
                GeneratedModelProperty("directLongCount", GeneratedTypeRef.scalar("long"), required = true),
                GeneratedModelProperty("ratio", GeneratedTypeRef.scalar("number"), required = true),
                GeneratedModelProperty("preciseRatio", GeneratedTypeRef.scalar("double"), required = true),
                GeneratedModelProperty("filePayload", GeneratedTypeRef.scalar("file"), required = true),
                GeneratedModelProperty("emptyValue", GeneratedTypeRef.scalar("nil"), required = true),
                GeneratedModelProperty("unknownValue", GeneratedTypeRef.scalar("unknown"), required = true),
              ),
          ),
        ),
    )

  private fun aggregateServicesApi(
    baseUri: String? = null,
    apiRestClient: GeneratedJaxrsRestClient? = null,
    serviceRestClient: GeneratedJaxrsRestClient? = null,
  ): GeneratedApi =
    GeneratedApi(
      name = "TurnPost API",
      source = GeneratedSourceSpec(kind = GeneratedSourceSpec.Kind.OPENAPI, location = "memory://turnpost"),
      jaxrs = apiRestClient?.let { restClient -> GeneratedJaxrs(restClient = restClient) },
      services =
        listOf(
          GeneratedService(
            name = "UsersService",
            baseUri = baseUri,
            group = "Users",
            jaxrs = serviceRestClient?.let { restClient -> GeneratedJaxrs(restClient = restClient) },
            operations =
              listOf(
                GeneratedOperation(
                  id = "createUser",
                  method = "POST",
                  path = "/users",
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
                GeneratedOperation(
                  id = "getCurrentUser",
                  method = "GET",
                  path = "/users/me",
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
                GeneratedOperation(
                  id = "getUser",
                  method = "GET",
                  path = "/users/{userId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
              ),
          ),
          GeneratedService(
            name = "ProjectsService",
            baseUri = baseUri,
            group = "Projects",
            operations =
              listOf(
                GeneratedOperation(
                  id = "getProjectRepo",
                  method = "GET",
                  path = "/projects/{projectId}/repos/{repoId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "projectId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                      GeneratedParameter(
                        name = "repoId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
                GeneratedOperation(
                  id = "getProjectMember",
                  method = "GET",
                  path = "/projects/{projectId}/members/{memberId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "projectId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                      GeneratedParameter(
                        name = "memberId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
              ),
          ),
          GeneratedService(
            name = "UtilityService",
            baseUri = baseUri,
            group = "Utility",
            operations =
              listOf(
                GeneratedOperation(
                  id = "health",
                  method = "GET",
                  path = "/health",
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
                GeneratedOperation(
                  id = "status",
                  method = "GET",
                  path = "/status",
                  responses = listOf(GeneratedResponse(status = 204)),
                ),
              ),
          ),
        ),
    )

  private fun kotlinSource(
    packageName: String,
    typeSpec: TypeSpec,
  ): String =
    buildString {
      kotlinFileSpec(packageName, typeSpec).writeTo(this)
    }

  @OptIn(ExperimentalCompilerApi::class)
  private fun assertIrSnapshot(
    testUri: URI,
    typeRegistry: KotlinTypeRegistry,
    options: KotlinJAXRSOptions,
    filePackageName: String,
    snapshotPath: String,
    typeName: String = "io.test.service.API",
  ) {
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    KotlinJAXRSIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(builtTypes))
    assertKotlinJaxrsSnapshot(
      snapshotPath,
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, typeName.replace('.', '/') + ".kt"),
    )
  }
}
