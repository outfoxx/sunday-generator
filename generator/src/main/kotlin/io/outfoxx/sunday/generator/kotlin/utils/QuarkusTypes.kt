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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec

val QUARKUS_HTTP_PROBLEM = ClassName.bestGuess("io.quarkiverse.resteasy.problem.HttpProblem")

const val QUARKUS_HTTP_PROBLEM_ALIAS = "QuarkusHttpProblem"

/** Marker tag for generated Kotlin types that reference Quarkus' HttpProblem base type. */
object QuarkusHttpProblemAlias

/** Adds the Quarkus HttpProblem import alias marker to a type builder. */
fun TypeSpec.Builder.addQuarkusHttpProblemAlias(): TypeSpec.Builder =
  tag(QuarkusHttpProblemAlias::class, QuarkusHttpProblemAlias)

/** Creates a KotlinPoet file with generated import aliases needed by the type. */
fun kotlinFileSpec(
  packageName: String,
  typeSpec: TypeSpec,
): FileSpec {
  val builder = FileSpec.builder(packageName, typeSpec.name ?: "Generated")

  if (typeSpec.tag(QuarkusHttpProblemAlias::class) != null) {
    builder.addAliasedImport(QUARKUS_HTTP_PROBLEM, QUARKUS_HTTP_PROBLEM_ALIAS)
  }

  return builder
    .addType(typeSpec)
    .build()
}
