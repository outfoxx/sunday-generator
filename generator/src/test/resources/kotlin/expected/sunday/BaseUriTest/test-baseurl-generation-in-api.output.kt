package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.http.Method
import io.test.Environment
import kotlin.String
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(): String = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )

  public companion object {
    public fun baseURL(
      server: String = "master",
      environment: Environment = Environment.Sbx,
      version: String = "1",
    ): URITemplate = URITemplate(
      "http://{server}.{environment}.example.com/api/{version}",
      mapOf("server" to server, "environment" to environment, "version" to version)
    )
  }
}
