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

import amf.client.remote.Content
import amf.client.resource.ClientResourceLoader
import amf.client.resource.FileResourceLoader
import amf.client.resource.HttpResourceLoader
import amf.core.remote.FileNotFound
import amf.core.remote.UnsupportedUrlScheme
import java.util.concurrent.CompletableFuture

object LocalResourceLoader : ClientResourceLoader {

  private val fileLoader = FileResourceLoader()
  private val httpLoader = HttpResourceLoader()

  override fun fetch(resource: String): CompletableFuture<Content> {
    return if (resource.equals("https://outfoxx.github.io/sunday-generator/sunday.raml", ignoreCase = true)) {
      val bytes = LocalResourceLoader::class.java.getResource("/sunday.raml")?.openStream()?.readAllBytes()
      if (bytes != null) {
        val content = Content(String(bytes, Charsets.UTF_8), resource)
        CompletableFuture.completedFuture(content)
      } else {
        httpLoader.fetch(resource)
      }
    } else if (resource.startsWith("file:")) {
      fileLoader.fetch(resource)
    } else if (resource.startsWith("http:") || resource.startsWith("https:")) {
      httpLoader.fetch(resource)
    } else {
      throw FileNotFound(UnsupportedUrlScheme(resource))
    }
  }
}
