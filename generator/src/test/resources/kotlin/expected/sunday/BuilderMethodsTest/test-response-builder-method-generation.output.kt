package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.http.Response
import kotlin.collections.List

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(): Response = this.transport
    .transportResponse(
      method = Method.Get,
      pathTemplate = "/test/response",
      acceptTypes = this.defaultAcceptTypes
    )
}
