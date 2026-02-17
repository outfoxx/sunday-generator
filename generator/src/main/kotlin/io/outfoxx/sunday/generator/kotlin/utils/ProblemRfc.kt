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

enum class ProblemField(
  val jsonName: String,
) {
  TYPE("type"),
  TITLE("title"),
  STATUS("status"),
  DETAIL("detail"),
  INSTANCE("instance"),
}

enum class KotlinProblemRfc(
  val id: String,
  val requiredFields: Set<ProblemField>,
  val allowedFields: Set<ProblemField>,
  val defaultFieldSet: Set<ProblemField>,
) {
  RFC7807(
    "rfc7807",
    emptySet(),
    enumValues<ProblemField>().toSet(),
    enumValues<ProblemField>().toSet(),
  ),
  RFC9457(
    "rfc9457",
    emptySet(),
    enumValues<ProblemField>().toSet(),
    enumValues<ProblemField>().toSet(),
  ),
}
