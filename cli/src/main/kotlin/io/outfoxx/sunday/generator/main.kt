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

import com.github.ajalt.clikt.core.subcommands
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerateCommand
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerateCommand

fun main(args: Array<String>) =
  GenerateCommand()
    .subcommands(KotlinJAXRSGenerateCommand(), KotlinSundayGenerateCommand())
    .subcommands(SwiftSundayGenerateCommand())
    .subcommands(TypeScriptSundayGenerateCommand())
    .main(args)
