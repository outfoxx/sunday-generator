package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.http.Response
import io.outfoxx.sunday.operation
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.withSecurity
import io.test.PatchableTest
import io.test.Test
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmOverloads

public class API<TReq, TRes> @JvmOverloads constructor(
  public val transport: Transport<TReq, TRes>,
  public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  private val clientSettings: ClientSettings? = null,
) {
  public fun fetchTest(): Operation<Unit, Test, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest")) else
      this.transport).operation<Unit, Test, TReq, TRes>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun putTest(body: Test): Operation<Test, Test, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("putTest")) else
      this.transport).operation<Test, Test, TReq, TRes>(
    OperationSpec(
      method = Method.Put,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun postTest(body: Test): Operation<Test, Test, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("postTest")) else
      this.transport).operation<Test, Test, TReq, TRes>(
    OperationSpec(
      method = Method.Post,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun patchTest(body: Test): Operation<Test, Test, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("patchTest")) else
      this.transport).operation<Test, Test, TReq, TRes>(
    OperationSpec(
      method = Method.Patch,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public fun deleteTest(): Operation<Unit, Unit, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("deleteTest")) else
      this.transport).operation<Unit, Unit, TReq, TRes>(
    OperationSpec(
      method = Method.Delete,
      pathTemplate = "/tests"
    )
  )

  public fun headTest(): Operation<Unit, Unit, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("headTest")) else
      this.transport).operation<Unit, Unit, TReq, TRes>(
    OperationSpec(
      method = Method.Head,
      pathTemplate = "/tests"
    )
  )

  public fun optionsTest(): Operation<Unit, Unit, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("optionsTest")) else
      this.transport).operation<Unit, Unit, TReq, TRes>(
    OperationSpec(
      method = Method.Options,
      pathTemplate = "/tests"
    )
  )

  public fun patchableTest(body: PatchableTest): Operation<PatchableTest, Test, TReq, TRes> = (if
      (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("patchableTest")) else
      this.transport).operation<PatchableTest, Test, TReq, TRes>(
    OperationSpec(
      method = Method.Patch,
      pathTemplate = "/tests2",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public suspend fun requestTest(): Request<TReq> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("requestTest")) else
      this.transport)
    .transportRequest(
      method = Method.Get,
      pathTemplate = "/request",
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun responseTest(): Response<TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("responseTest")) else
      this.transport)
    .transportResponse(
      method = Method.Get,
      pathTemplate = "/response",
      acceptTypes = this.defaultAcceptTypes
    )
}
