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

package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.eagerOption
import com.github.ajalt.clikt.parameters.transform.theme
import java.util.jar.JarFile

fun CliktCommand.versionOption() =
  eagerOption(setOf("--version"), help = "Show version information and exit") {
    throw PrintMessage(
      """

        ${theme.style("warning")("Sunday")} ${theme.style("info")("- Generator")}  ver. ${
        theme.style("danger")(
          versionString,
        )
      }

        ${theme.style("warning")("Supports")}:

        * ${theme.style("warning")("Kotlin")}
          JAX-RS ${theme.style("muted")("Client, Server")}

        * ${theme.style("warning")("Swift")}
          Sunday ${theme.style("muted")("Client")}

        * ${theme.style("warning")("TypeScript")}
          Sunday ${theme.style("muted")("Client")}
      """.trimIndent(),
    )
  }

val versionString: String
  get() {
    val version = GenerateCommand::class.java.`package`.implementationVersion ?: "unknown"
    val commit = readImplBuild()
    return if (commit != null) {
      "$version (build: $commit)"
    } else {
      version
    }
  }

fun readImplBuild(): String? =
  try {
    val classPath = GenerateCommand::class.java.protectionDomain.codeSource.location.path
    JarFile(classPath).use { jarFile ->

      val manifest = jarFile.manifest
      val mainAttributes = manifest.mainAttributes

      return try {
        mainAttributes.getValue("Implementation-Build").ifBlank { null }
      } catch (e: Exception) {
        null
      }
    }
  } catch (e: Exception) {
    null
  }
