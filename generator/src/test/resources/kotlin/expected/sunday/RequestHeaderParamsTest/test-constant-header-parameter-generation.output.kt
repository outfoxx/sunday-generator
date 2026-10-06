package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.withSecurity
import io.test.Test
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmOverloads

public class API<Req : Request> @JvmOverloads constructor(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  private val clientSettings: ClientSettings? = null,
) {
  public fun putTest(xCustom: String): Operation<Unit, Test, Req> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("putTest")) else
      this.transport).operation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Put,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes,
      headers = mapOf(
        "Expect" to "100-continue",
        "x-custom" to xCustom
      )
    )
  )
}
