package io.test.service

import io.outfoxx.sunday.EventSource
import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import kotlin.collections.List

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
) {
  public suspend fun fetchEvents(): EventSource = this.transport
    .eventSource(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = listOf(MediaType.EventStream)
    )
}
