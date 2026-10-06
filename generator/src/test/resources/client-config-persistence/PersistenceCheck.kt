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

package example

import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.jdk.JdkTransport
import io.outfoxx.sunday.problems.SundayHttpProblem
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.security.ProviderCredentials
import io.outfoxx.sunday.security.SecurityBinding
import io.outfoxx.sunday.security.TokenConfiguration
import io.outfoxx.sunday.security.TokenManager
import io.outfoxx.sunday.security.TokenManagerFactory
import io.outfoxx.sunday.security.TokenProvider
import io.outfoxx.sunday.security.TokenRequest
import io.outfoxx.sunday.security.TokenSet
import io.outfoxx.sunday.security.TokenStore
import kotlinx.coroutines.async
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Executes persistence through both generated configuration entry points. */
object PersistenceCheck {
  /** Called by the compiler-backed generator test. */
  @JvmStatic
  fun verify() {
    fun assertEquals(
      expected: Any?,
      actual: Any?,
    ) {
      check(expected == actual) { "Expected $expected, got $actual" }
    }
    for (aggregate in listOf(false, true)) {
      kotlinx.coroutines.runBlocking {
        val values = mutableMapOf<String, TokenSet>()
        var reads = 0
        var saves = 0
        var acquisitions = 0
        var factories = 0
        val refreshes = mutableListOf<String>()
        val store =
          object : TokenStore {
            override suspend fun load(key: String): TokenSet? {
              reads++
              return values[key]
            }

            override suspend fun save(
              key: String,
              tokens: TokenSet,
            ) {
              saves++
              values[key] = tokens
            }

            override suspend fun remove(key: String) {
              values.remove(key)
            }
          }
        val managers = mutableListOf<TokenManager>()
        val transports = mutableListOf<io.outfoxx.sunday.Transport<io.outfoxx.sunday.jdk.JdkRequest>>()
        val readRequests = mutableMapOf<ClientSettings, suspend () -> Unit>()
        val publicRequests = mutableMapOf<ClientSettings, suspend () -> Unit>()

        fun settings(
          time: Long,
          session: String = "session",
          profile: String = "external-development",
        ): ClientSettings {
          val provider =
            object : TokenProvider.Refreshing {
              override val identity = "application"

              override fun configure(binding: SecurityBinding) = TokenConfiguration("client", session)

              override suspend fun acquire(request: TokenRequest): TokenSet {
                acquisitions++
                return TokenSet("initial", Instant.ofEpochSecond(100), "refresh-1")
              }

              override suspend fun refresh(
                request: TokenRequest,
                refreshToken: String,
              ): TokenSet {
                refreshes += refreshToken
                return TokenSet(
                  "rotated-${refreshes.size}",
                  Instant.ofEpochSecond(time + 100),
                  "refresh-${refreshes.size + 1}",
                )
              }
            }
          val factory: TokenManagerFactory = { providers ->
            factories++
            assertEquals(mapOf("application" to provider), providers)
            TokenManager(
              providers,
              store,
              Duration.ofSeconds(5),
              Clock.fixed(Instant.ofEpochSecond(time), ZoneOffset.UTC),
              this,
            ).also {
              managers +=
                it
            }
          }
          var captured: ClientSettings? = null
          var calls = 0
          val transportFactory: (ClientSettings) -> JdkTransport = { settings ->
            captured = settings
            calls++
            JdkTransport(
              URITemplate(settings.baseURL.toString()),
              SundayHttpProblem.Factory,
              tokenManager = settings.tokenManager,
            )
          }
          if (aggregate) {
            val client =
              createExampleAPI(
                ExampleAPIDevelopmentConfig(),
                transportFactory,
                ExampleAPICredentials(identity = ProviderCredentials(provider)),
                securityProfile = profile,
                tokenManagerFactory = factory,
              )
            check(client.users.transport === client.transport && client.projects.transport === client.transport)
            transports += client.transport
            readRequests[requireNotNull(captured)] = {
              client.users.listUsers().transportRequest()
              client.projects.listProjects().transportRequest()
            }
            publicRequests[requireNotNull(captured)] = {
              val request = client.users.register().transportRequest()
              check(request.headers.none { it.first.equals("Authorization", true) })
            }
          } else {
            val client =
              createUsersAPI(
                ExampleAPIDevelopmentConfig(),
                transportFactory,
                UsersAPICredentials(identity = ProviderCredentials(provider)),
                securityProfile = profile,
                tokenManagerFactory = factory,
              )
            transports += client.transport
            readRequests[requireNotNull(captured)] = {
              client.listUsers().transportRequest()
              Unit
            }
            publicRequests[requireNotNull(captured)] = {
              val request = client.register().transportRequest()
              check(request.headers.none { it.first.equals("Authorization", true) })
            }
          }
          check(calls == 1)
          return requireNotNull(captured)
        }

        suspend fun token(settings: ClientSettings): TokenSet {
          readRequests.getValue(settings)()
          return settings.tokenManager!!.credentials(settings.bindings.getValue("listUsers").single()).tokens
        }
        try {
          val first = settings(0)
          assertEquals(listOf(0, 0, 0, 1), listOf(reads, saves, acquisitions, factories))
          publicRequests.getValue(first)()
          assertEquals(0, reads)
          val lease = first.tokenManager!!.credentials(first.bindings.getValue("listUsers").single())
          assertEquals("initial", lease.tokens.accessToken)
          first.tokenManager!!.close()
          assertEquals("initial", token(settings(0)).accessToken)
          assertEquals(1, acquisitions)
          val third = settings(96)
          assertEquals(
            setOf("rotated-1"),
            (1..20).map { async { token(third).accessToken } }.map { it.await() }.toSet(),
          )
          assertEquals(listOf("refresh-1"), refreshes)
          assertEquals("refresh-2", token(settings(96)).refreshToken)
          token(settings(192))
          assertEquals(listOf("refresh-1", "refresh-2"), refreshes)
          assertEquals(3, saves)
          token(settings(0, session = "other-session"))
          token(settings(0, profile = "external"))
          assertEquals(3, acquisitions)
          store.remove(values.keys.first())
          token(settings(0))
          assertEquals(4, acquisitions)
        } finally {
          transports.forEach { it.close() }
          managers.forEach { it.close() }
        }
      }
    }
  }
}
