package io.test.service

import io.outfoxx.sunday.EventSource
import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.withSecurity
import kotlin.collections.List
import kotlin.jvm.JvmOverloads

public class API<Req : Request> @JvmOverloads constructor(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
  private val clientSettings: ClientSettings? = null,
) {
  public suspend fun fetchEvents(): EventSource = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchEvents")) else
      this.transport)
    .eventSource(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = listOf(MediaType.EventStream)
    )
}
