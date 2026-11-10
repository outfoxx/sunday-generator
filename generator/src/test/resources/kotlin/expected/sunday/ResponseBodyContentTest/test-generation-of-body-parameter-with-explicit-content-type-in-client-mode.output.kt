package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.operation
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.withSecurity
import kotlin.ByteArray
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmOverloads

public class API<TReq, TRes> @JvmOverloads constructor(
  public val transport: Transport<TReq, TRes>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
  private val clientSettings: ClientSettings? = null,
) {
  public fun fetchTest(): Operation<Unit, ByteArray, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest")) else
      this.transport).operation<Unit, ByteArray, TReq, TRes>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = listOf(MediaType.OctetStream)
    )
  )
}
