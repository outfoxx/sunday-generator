package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.http.Response
import io.outfoxx.sunday.operation
import io.test.PatchableTest
import io.test.Test
import kotlin.Unit
import kotlin.collections.List

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public fun fetchTest(): Operation<Unit, Test, Req> = this.transport.operation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun putTest(body: Test): Operation<Test, Test, Req> =
      this.transport.operation<Test, Test, Req>(
    OperationSpec(
      method = Method.Put,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun postTest(body: Test): Operation<Test, Test, Req> =
      this.transport.operation<Test, Test, Req>(
    OperationSpec(
      method = Method.Post,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun patchTest(body: Test): Operation<Test, Test, Req> =
      this.transport.operation<Test, Test, Req>(
    OperationSpec(
      method = Method.Patch,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun deleteTest(): Operation<Unit, Unit, Req> = this.transport.operation<Unit, Unit, Req>(
    OperationSpec(
      method = Method.Delete,
      pathTemplate = "/tests"
    )
  )

  public fun headTest(): Operation<Unit, Unit, Req> = this.transport.operation<Unit, Unit, Req>(
    OperationSpec(
      method = Method.Head,
      pathTemplate = "/tests"
    )
  )

  public fun optionsTest(): Operation<Unit, Unit, Req> = this.transport.operation<Unit, Unit, Req>(
    OperationSpec(
      method = Method.Options,
      pathTemplate = "/tests"
    )
  )

  public fun patchableTest(body: PatchableTest): Operation<PatchableTest, Test, Req> =
      this.transport.operation<PatchableTest, Test, Req>(
    OperationSpec(
      method = Method.Patch,
      pathTemplate = "/tests2",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public suspend fun requestTest(): Req = this.transport
    .transportRequest(
      method = Method.Get,
      pathTemplate = "/request",
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun responseTest(): Response = this.transport
    .transportResponse(
      method = Method.Get,
      pathTemplate = "/response",
      acceptTypes = this.defaultAcceptTypes
    )
}
