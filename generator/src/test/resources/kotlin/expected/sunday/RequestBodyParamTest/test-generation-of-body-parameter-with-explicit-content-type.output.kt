package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import kotlin.Any
import kotlin.ByteArray
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public fun fetchTest(body: ByteArray): Operation<ByteArray, Map<String, Any>, Req> =
      this.transport.operation<ByteArray, Map<String, Any>, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      body = body,
      contentTypes = listOf(MediaType.OctetStream),
      acceptTypes = this.defaultAcceptTypes
    )
  )
}
