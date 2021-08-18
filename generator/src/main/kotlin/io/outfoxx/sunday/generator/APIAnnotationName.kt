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

enum class APIAnnotationName(val id: String, private val modeSpecific: Boolean) {

  ServiceGroup("group", false),

  KotlinPkg("kotlinPackage", true),
  KotlinModelPkg("kotlinModelPackage", true),

  KotlinType("kotlinType", true),
  KotlinImpl("kotlinImplementation", true),

  TypeScriptModule("typeScriptModule", false),
  TypeScriptModelModule("typeScriptModelModule", false),

  TypeScriptType("typeScriptType", false),
  TypeScriptImpl("typeScriptImplementation", false),

  SwiftType("swiftType", false),
  SwiftImpl("swiftImplementation", false),

  Nested("nested", false),

  ExternalDiscriminator("externalDiscriminator", false),
  ExternallyDiscriminated("externallyDiscriminated", false),

  Patchable("patchable", false),

  ProblemBaseUri("problemBaseUri", false),
  ProblemBaseUriParams("problemUriParams", false),
  ProblemTypes("problemTypes", false),
  Problems("problems", false),

  Nullify("nullify", false),

  // Sunday
  EventSource("eventSource", false),
  EventStream("eventStream", false),

  RequestOnly("requestOnly", false),
  ResponseOnly("responseOnly", false),

  // JAX-RS
  Asynchronous("asynchronous", false),
  Reactive("reactive", false),
  SSE("sse", false),
  JsonBody("jsonBody", true),

  ;

  fun matches(test: String, generationMode: GenerationMode? = null) =
    if (modeSpecific && generationMode != null) {
      test == "$id:${generationMode.name.toLowerCase()}" || test == "sunday-$id-${generationMode.name.toLowerCase()}"
    } else {
      test == id || test == "sunday-$id"
    }

  override fun toString() = id
}
