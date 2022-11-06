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

import amf.core.client.common.remote.Content
import amf.core.client.platform.resource.HttpResourceLoader
import amf.core.client.platform.resource.ResourceLoader
import java.util.concurrent.CompletableFuture

object LocalResourceLoader : ResourceLoader {

  private val httpLoader = HttpResourceLoader()

  override fun accepts(resource: String?): Boolean {
    return resource.equals("https://outfoxx.github.io/sunday-generator/sunday.raml", ignoreCase = true)
  }

  override fun fetch(resource: String): CompletableFuture<Content> {
    val bytes = LocalResourceLoader::class.java.getResource("/sunday.raml")?.openStream()?.readAllBytes()
    return if (bytes != null) {
      val content = Content(String(bytes, Charsets.UTF_8), resource)
      CompletableFuture.completedFuture(content)
    } else {
      return httpLoader.fetch(resource)
    }
  }
}
