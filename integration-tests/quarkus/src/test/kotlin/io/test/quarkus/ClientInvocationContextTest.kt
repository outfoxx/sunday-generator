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

package io.test.quarkus

import com.sun.net.httpserver.HttpServer
import io.outfoxx.sunday.client.quarkus.ClientInvocation
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.faulttolerance.api.BeforeRetry
import io.smallrye.faulttolerance.api.BeforeRetryHandler
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.client.ClientRequestContext
import jakarta.ws.rs.client.ClientRequestFilter
import jakarta.ws.rs.client.ClientResponseContext
import jakarta.ws.rs.client.ClientResponseFilter
import jakarta.ws.rs.core.Context
import kotlinx.coroutines.runBlocking
import org.eclipse.microprofile.faulttolerance.ExecutionContext
import org.eclipse.microprofile.faulttolerance.Retry
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.JvmDefaultWithoutCompatibility

@QuarkusTest
@QuarkusTestResource(ClientInvocationContextTest.Upstream::class)
class ClientInvocationContextTest {
  @Inject
  @RestClient
  lateinit var client: Client

  @Test
  fun `default client methods share a non-wire context across native retries`() {
    Executors.newFixedThreadPool(4).use { executor ->
      val calls = (1..8).map { executor.submit<String> { client.read() } }
      calls.forEach { assertEquals("2:0", it.get()) }
    }
  }

  @Test
  fun `each reactive subscription owns a fresh retry context`() {
    val deferred = client.readDeferred()
    assertEquals("2:0", deferred.await().indefinitely())
    assertEquals("2:0", deferred.await().indefinitely())
  }

  @Test
  fun `completion stages retain their context across native retries`() {
    assertEquals("2:0", client.readAsync().toCompletableFuture().get())
    assertEquals("2:0", client.readAsync().toCompletableFuture().get())
  }

  @Test
  fun `coroutine methods retain their context across native retries`() =
    runBlocking {
      assertEquals("2:0", client.readSuspend())
      assertEquals("2:0", client.readSuspend())
    }

  @Test
  fun `native retry owns the complete attempt budget including one authentication recovery`() {
    assertEquals("3:0", client.bounded("500,401,200"))
    assertEquals("3:0", client.bounded("401,500,200"))
    for ((scenario, attempts) in listOf("401,401,200" to 2, "401,500,401,200" to 3, "403,200" to 1)) {
      val failure = assertThrows(WebApplicationException::class.java) { client.bounded(scenario) }
      assertEquals(attempts.toString(), failure.response.getHeaderString("X-Test-Attempts"))
      assertEquals(scenario.split(',')[attempts - 1].toInt(), failure.response.status)
    }
    val unsafe = assertThrows(WebApplicationException::class.java) { client.unsafe() }
    assertEquals("1", unsafe.response.getHeaderString("X-Test-Attempts"))
  }

  @Test
  fun `bounded native retry retains its budget through lazy and suspending executions`() =
    runBlocking {
      val deferred = client.boundedDeferred("401,200")
      assertEquals("2:0", deferred.await().indefinitely())
      assertEquals("2:0", deferred.await().indefinitely())
      assertEquals("2:0", client.boundedSuspend("401,200"))
      val failure =
        assertThrows(WebApplicationException::class.java) {
          client.boundedDeferred("401,401,200").await().indefinitely()
        }
      assertEquals("2", failure.response.getHeaderString("X-Test-Attempts"))
      val suspendedFailure =
        try {
          client.boundedSuspend("401,401,200")
          error("Expected failed authentication")
        } catch (error: WebApplicationException) {
          error
        }
      assertEquals("2", suspendedFailure.response.getHeaderString("X-Test-Attempts"))
    }

  /** Carries invocation state explicitly, including across reactive retries. */
  class Invocation {
    val attempts = AtomicInteger()
    val retries = AtomicInteger()
  }

  /** Demonstrates the native extension points needed by the generated authenticated client. */
  @JvmDefaultWithoutCompatibility
  @Path("/")
  @RegisterRestClient(configKey = "invocation-context")
  @RegisterProvider(ContextFilter::class)
  @RegisterProvider(RecoveryFilter::class)
  interface Client {
    /** Shares native retry capacity with authentication recovery. */
    fun bounded(scenario: String): String = ClientInvocation.execute { boundedPolicy(it, scenario) }

    /** Preserves the application's native policy and exception classification. */
    @Retry(
      maxRetries = 4,
      delay = 0,
      jitter = 0,
      abortOn = [ClientInvocation.Stopped::class, CancellationException::class],
    )
    fun boundedPolicy(
      invocation: ClientInvocation,
      scenario: String,
    ): String = invocation.attempt { boundedTransport(it, scenario) }

    /** Performs exactly one transport attempt. */
    @GET
    @Path("bounded")
    fun boundedTransport(
      @Context invocation: ClientInvocation.Attempt,
      @HeaderParam("X-Test-Scenario") scenario: String,
    ): String

    /** Checks unsafe requests with the same native retry policy. */
    fun unsafe(): String = ClientInvocation.execute { unsafePolicy(it) }

    /** Prevents retry from replaying rejected unsafe authentication. */
    @Retry(
      maxRetries = 4,
      delay = 0,
      jitter = 0,
      abortOn = [ClientInvocation.Stopped::class, CancellationException::class],
    )
    fun unsafePolicy(invocation: ClientInvocation): String = invocation.attempt { unsafeTransport(it, "401,200") }

    /** Sends an unsafe request to the test upstream. */
    @POST
    @Path("bounded")
    fun unsafeTransport(
      @Context invocation: ClientInvocation.Attempt,
      @HeaderParam("X-Test-Scenario") scenario: String,
    ): String

    /** Allocates a budget independently for each subscription. */
    fun boundedDeferred(scenario: String): Uni<String> =
      ClientInvocation.executeUni { boundedDeferredPolicy(it, scenario) }

    /** Native SmallRye reactive retries retain one budget. */
    @Retry(
      maxRetries = 4,
      delay = 0,
      jitter = 0,
      abortOn = [ClientInvocation.Stopped::class, CancellationException::class],
    )
    fun boundedDeferredPolicy(
      invocation: ClientInvocation,
      scenario: String,
    ): Uni<String> = invocation.attemptUni { boundedDeferredTransport(it, scenario) }

    /** Performs one reactive network attempt. */
    @GET
    @Path("bounded")
    fun boundedDeferredTransport(
      @Context invocation: ClientInvocation.Attempt,
      @HeaderParam("X-Test-Scenario") scenario: String,
    ): Uni<String>

    /** Suspends without relying on a thread-local budget. */
    suspend fun boundedSuspend(scenario: String): String =
      ClientInvocation.executeSuspend { boundedSuspendPolicy(it, scenario) }

    /** Native coroutine retry shares the same invocation state. */
    @Retry(
      maxRetries = 4,
      delay = 0,
      jitter = 0,
      abortOn = [ClientInvocation.Stopped::class, CancellationException::class],
    )
    suspend fun boundedSuspendPolicy(
      invocation: ClientInvocation,
      scenario: String,
    ): String = invocation.attemptSuspend { boundedSuspendTransport(it, scenario) }

    /** Performs one suspending network attempt. */
    @GET
    @Path("bounded")
    suspend fun boundedSuspendTransport(
      @Context invocation: ClientInvocation.Attempt,
      @HeaderParam("X-Test-Scenario") scenario: String,
    ): String

    /** Allocates context once outside SmallRye's retry interception. */
    fun read(): String {
      val invocation = Invocation()
      val result = execute(invocation)
      check(invocation.retries.get() == 1)
      return result
    }

    /** Allocates context independently for every execution of a deferred request. */
    fun readDeferred(): Uni<String> =
      Uni.createFrom().deferred {
        val invocation = Invocation()
        executeDeferred(invocation).invoke { _ -> check(invocation.retries.get() == 1) }
      }

    /** Allocates context before starting an eager asynchronous invocation. */
    fun readAsync(): CompletionStage<String> {
      val invocation = Invocation()
      return executeAsync(invocation).thenApply { result ->
        check(invocation.retries.get() == 1)
        result
      }
    }

    /** Retains context in the coroutine state machine across suspension. */
    suspend fun readSuspend(): String {
      val invocation = Invocation()
      val result = executeSuspend(invocation)
      check(invocation.retries.get() == 1)
      return result
    }

    /** Native transport operation; context parameters are excluded from the wire. */
    @GET
    @Retry(maxRetries = 2, delay = 0, jitter = 0)
    @BeforeRetry(RetryObserver::class)
    fun execute(
      @Context invocation: Invocation,
    ): String

    /** Native reactive transport operation. */
    @GET
    @Path("deferred")
    @Retry(maxRetries = 2, delay = 0, jitter = 0)
    @BeforeRetry(RetryObserver::class)
    fun executeDeferred(
      @Context invocation: Invocation,
    ): Uni<String>

    /** Native completion-stage transport operation. */
    @GET
    @Path("async")
    @Retry(maxRetries = 2, delay = 0, jitter = 0)
    @BeforeRetry(RetryObserver::class)
    fun executeAsync(
      @Context invocation: Invocation,
    ): CompletionStage<String>

    /** Native suspending transport operation. */
    @GET
    @Path("suspend")
    @Retry(maxRetries = 2, delay = 0, jitter = 0)
    @BeforeRetry(RetryObserver::class)
    suspend fun executeSuspend(
      @Context invocation: Invocation,
    ): String
  }

  /** Accesses the same invocation object using Quarkus's REST client request metadata. */
  class ContextFilter : ClientRequestFilter {
    override fun filter(context: ClientRequestContext) {
      val parameters = context.getProperty("io.quarkus.rest.client.invokedMethodParameters") as List<*>
      val invocation = parameters.filterIsInstance<Invocation>().singleOrNull() ?: return
      context.headers.putSingle("X-Test-Attempt", invocation.attempts.incrementAndGet().toString())
    }
  }

  /** Records real attempts and challenges without owning a retry loop. */
  class RecoveryFilter :
    ClientRequestFilter,
    ClientResponseFilter {
    override fun filter(context: ClientRequestContext) {
      val invocation = invocation(context) ?: return
      invocation.startRequest(context.method, context.hasEntity())
      context.headers.putSingle("X-Test-Attempt", invocation.number.toString())
    }

    override fun filter(
      request: ClientRequestContext,
      response: ClientResponseContext,
    ) {
      invocation(request)?.received(
        response.status,
        response.headers.getFirst("WWW-Authenticate") == "Bearer error=invalid_token",
      )
    }

    private fun invocation(context: ClientRequestContext): ClientInvocation.Attempt? =
      (
        context.getProperty(
          "io.quarkus.rest.client.invokedMethodParameters",
        ) as List<*>
      ).filterIsInstance<ClientInvocation.Attempt>().singleOrNull()
  }

  /** Observes retries without doing IO, throwing, or introducing a second replay loop. */
  class RetryObserver : BeforeRetryHandler {
    override fun handle(context: ExecutionContext) {
      context.parameters
        .filterIsInstance<Invocation>()
        .single()
        .retries
        .incrementAndGet()
    }
  }

  /** Checks real network requests independently of the client provider chain. */
  class Upstream : QuarkusTestResourceLifecycleManager {
    private lateinit var server: HttpServer

    override fun start(): Map<String, String> {
      server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/") { exchange ->
        val attempt = exchange.requestHeaders.getFirst("X-Test-Attempt").toInt()
        val body = exchange.requestBody.readAllBytes()
        check(exchange.requestURI.rawQuery == null)
        val response = "$attempt:${body.size}".toByteArray()
        exchange.responseHeaders.set("Content-Type", "text/plain")
        val scenario =
          exchange.requestHeaders
            .getFirst("X-Test-Scenario")
            ?.split(',')
            ?.map(String::toInt)
        val status = scenario?.getOrElse(attempt - 1) { 200 } ?: if (attempt == 1) 500 else 200
        exchange.responseHeaders.set("X-Test-Attempts", attempt.toString())
        if (status == 401) exchange.responseHeaders.set("WWW-Authenticate", "Bearer error=invalid_token")
        exchange.sendResponseHeaders(status, response.size.toLong())
        exchange.responseBody.use { it.write(response) }
        exchange.close()
      }
      server.start()
      return mapOf("quarkus.rest-client.invocation-context.url" to "http://127.0.0.1:${server.address.port}")
    }

    override fun stop() {
      server.stop(0)
    }
  }
}
