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
      val bytes = LocalResourceLoader::class.java.getResource("/sunday.raml").openStream().readAllBytes()
      val content = Content(String(bytes, Charsets.UTF_8), resource)
      CompletableFuture.completedFuture(content)
    } else if (resource.startsWith("file:")) {
      fileLoader.fetch(resource)
    } else if (resource.startsWith("http:") || resource.startsWith("https:")) {
      httpLoader.fetch(resource)
    } else {
      throw FileNotFound(UnsupportedUrlScheme(resource))
    }
  }

}
