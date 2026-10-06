package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.http.Response
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.withSecurity
import kotlin.collections.List
import kotlin.jvm.JvmOverloads

public class API<Req : Request> @JvmOverloads constructor(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  private val clientSettings: ClientSettings? = null,
) {
  public suspend fun fetchTest(): Response = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest")) else
      this.transport)
    .transportResponse(
      method = Method.Get,
      pathTemplate = "/test/response",
      acceptTypes = this.defaultAcceptTypes
    )
}
