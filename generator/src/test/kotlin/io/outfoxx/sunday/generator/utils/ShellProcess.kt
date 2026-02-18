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

package io.outfoxx.sunday.generator.utils

object ShellProcess {

  const val DEFAULT_SHELL = "/bin/sh"

  fun execute(
    command: String,
    vararg args: String,
    useEnvShell: Boolean = false,
  ): Pair<Boolean, String> {
    val process =
      ProcessBuilder()
        .command(getShell(useEnvShell), command, *args)
        .apply {
          environment().putAll(loadExtraEnvironment())
        }.start()

    val result = process.waitFor()
    val out =
      if (result == 0) {
        process.inputStream.readAllBytes().decodeToString()
      } else {
        process.errorStream.readAllBytes().decodeToString()
      }
    return (result == 0) to out
  }

  fun getShell(fromEnv: Boolean = false) =
    if (fromEnv) {
      System.getenv("SHELL").ifBlank { null } ?: DEFAULT_SHELL
    } else {
      DEFAULT_SHELL
    }

  fun loadExtraEnvironment(): Map<String, String> {
    if (System.getenv("CI") != null) {
      return mapOf()
    }
    val useMise = System.getenv().keys.any { it.contains("""(^|_)MISE(_|$)""".toRegex()) }
    return if (useMise) {
      loadMiseEnvironment()
    } else {
      mapOf()
    }
  }

  /**
   * Load environment variables from the `mise env`, if available.
   */
  fun loadMiseEnvironment(): Map<String, String> {
    try {
      val loadEnv =
        ProcessBuilder()
          .command("mise", "env")
          .start()

      return if (loadEnv.waitFor() == 0) {
        loadEnv.inputStream
          .readAllBytes()
          .decodeToString()
          .split("\n")
          .map { it.removePrefix("export ") }
          .filter { it.isNotBlank() }
          .map { it.split("=", limit = 2) }
          .associate { it[0] to it[1] }
      } else {
        mapOf()
      }
    } catch (ignored: Exception) {
      return mapOf()
    }
  }
}
