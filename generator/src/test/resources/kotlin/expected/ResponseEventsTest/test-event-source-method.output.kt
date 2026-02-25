package io.test.service

import io.outfoxx.sunday.EventSource
import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
) {
  public suspend fun fetchEvents(): EventSource = this.requestFactory
    .eventSource(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = listOf(MediaType.EventStream)
    )
}
