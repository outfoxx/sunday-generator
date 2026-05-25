package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import io.test.Test
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.collections.List

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public fun fetchTest(
    obj: Test,
    strReq: String,
    int: Int = 5,
  ): Operation<Unit, Test, Req> = this.transport.operation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      queryParameters = mapOf(
        "obj" to obj,
        "str-req" to strReq,
        "int" to int
      ),
      acceptTypes = this.defaultAcceptTypes
    )
  )
}
