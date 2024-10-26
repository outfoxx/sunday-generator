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

@file:OptIn(ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin.tools

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.outfoxx.sunday.test.utils.Compilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.ByteArrayOutputStream

fun compileTypes(types: Map<ClassName, TypeSpec>): KotlinCompilation.ExitCode {

  val fileSpecs =
    types.entries
      .filter { it.key.topLevelClassName() == it.key }
      .map { FileSpec.get(it.key.packageName, it.value) }

  val out = ByteArrayOutputStream()

  val result =
    KotlinCompilation()
      .apply {
        sources = fileSpecs.map {
          val fileName = "${it.packageName.replace('.', '_')}_${it.name}.kt"
          SourceFile.kotlin(fileName, it.toString())
        }
        kotlincArguments = listOf("-jvm-target", "11")
        languageVersion = "1.6"
        inheritClassPath = true
        verbose = false
        allWarningsAsErrors = true
        reportOutputFiles = true
        messageOutputStream = out
      }
      .compile()

  if (result.exitCode != KotlinCompilation.ExitCode.OK) {

    val files =
      fileSpecs.associate { fileSpec ->
        val builder = StringBuilder()
        fileSpec.writeTo(builder)

        fileSpec.name to builder.toString()
      }

    Compilation.printFailure(files, out.toByteArray().decodeToString())
  }

  return result.exitCode
}
