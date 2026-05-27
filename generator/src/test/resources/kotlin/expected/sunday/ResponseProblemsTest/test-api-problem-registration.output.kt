package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import io.test.InvalidIdProblem
import io.test.Test
import io.test.TestNotFoundProblem
import kotlin.Unit
import kotlin.collections.List

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  init {
    transport.registerProblem("http://example.com/invalid_id", InvalidIdProblem::class)
    transport.registerProblem("http://example.com/test_not_found", TestNotFoundProblem::class)
  }
  public fun fetchTest(): Operation<Unit, Test, Req> = this.transport.operation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )
  )
}
