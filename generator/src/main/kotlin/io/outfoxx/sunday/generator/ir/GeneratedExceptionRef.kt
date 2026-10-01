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

package io.outfoxx.sunday.generator.ir

/** A native exception class or an explicit reference to a generated problem exception. */
data class GeneratedExceptionRef(
  val className: String? = null,
  val problem: String? = null,
) {
  init {
    require(
      (className != null) != (problem != null),
    ) { "Exception references require exactly one of className or problem" }
    require(className == null || className.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))) {
      "Exception class names must be qualified names"
    }
    require(problem == null || problem.isNotBlank()) { "Problem exception references must not be blank" }
  }
}
