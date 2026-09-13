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

package io.outfoxx.sunday.generator.gradle.tests

import io.outfoxx.sunday.generator.ir.OpenApiDocumentSnapshot
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiNetworkPolicyTest {
  @Test
  fun `discovery rejects configured proxies until explicitly enabled`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { proxy ->
      val target = URI("http://8.8.8.8/schema")
      proxy.respond("/schema", "type: object\nproperties: {remote: {type: string}}")
      directory.resolve("settings.gradle").writeText("rootProject.name = 'proxy-references'")
      directory.resolve("build.gradle").writeText(
        """
        plugins {
          id 'java'
          id 'io.outfoxx.sunday-generator'
        }
        sundayGenerations {
          client {
            source.set(files('api.yaml'))
            framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.Sunday)
            mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
            openApiReferenceCacheDirectory.set(layout.projectDirectory.dir('remote-cache'))
            openApiAllowPrivateNetwork.set(providers.gradleProperty('allowPrivateOpenApi').map { it.toBoolean() }.orElse(false))
          }
        }
        """.trimIndent(),
      )
      val source = directory.resolve("api.yaml")
      source.writeText(
        "openapi: 3.1.0\ninfo: {title: Proxy, version: 1}\npaths: {}\ncomponents:\n  schemas:\n    Remote: {${'$'}ref: '$target'}",
      )

      fun runner(vararg arguments: String) =
        GradleRunner
          .create()
          .withProjectDir(directory.toFile())
          .withPluginClasspath()
          // Separate daemon keeps proxy system properties out of the test process and parallel fixtures.
          .withDebug(false)
          .withArguments(
            "sundayDiscoverIncludes_client",
            "--stacktrace",
            "-Dhttp.proxyHost=127.0.0.1",
            "-Dhttp.proxyPort=${proxy.baseUri.port}",
            "-Dhttp.nonProxyHosts=",
            *arguments,
          )
      val blocked = runner().buildAndFail()
      assertTrue(blocked.output.contains("Configured proxy"), blocked.output)
      assertTrue(proxy.requests.isEmpty())
      val permitted = runner("-PallowPrivateOpenApi=true").build()
      assertEquals(TaskOutcome.SUCCESS, permitted.task(":sundayDiscoverIncludes_client")?.outcome)
      assertEquals(listOf(target.toString()), proxy.requests.toList())
      val captured =
        OpenApiDocumentSnapshot.loader(
          directory.resolve("build/generated/sunday/documents/sundayDiscoverIncludes_client"),
          directory,
        )
      assertEquals("type: object\nproperties: {remote: {type: string}}", String(captured.load(target).bytes))
      proxy.requests.clear()
      val offline = runner("--offline").build()
      assertEquals(TaskOutcome.SUCCESS, offline.task(":sundayDiscoverIncludes_client")?.outcome)
      assertTrue(proxy.requests.isEmpty())
    }
  }
}
