package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import io.test.Environment
import kotlin.String
import kotlin.Unit
import kotlin.collections.List

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public fun fetchTest(): Operation<Unit, String, Req> =
      this.transport.operation<Unit, String, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )
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
