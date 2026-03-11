package io.test

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Response
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(): Response = this.requestFactory
    .response(
      method = Method.Get,
      pathTemplate = "/test/response",
      acceptTypes = this.defaultAcceptTypes
    )
}
