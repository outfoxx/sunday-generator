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
import com.github.ajalt.clikt.parameters.options.versionOption

fun CliktCommand.versionOption() = apply {
  versionOption(
    versionString,
    message = {
      """

        Sunday - Generator   ver. $versionString

        Supports: Kotlin (Sunday & JAX-RS), Swift (Sunday), TypeScript (Sunday)
      """.trimIndent()
    },
  )
}

val versionString: String
  get() = GenerateCommand::class.java.`package`.implementationVersion ?: "unknown"
