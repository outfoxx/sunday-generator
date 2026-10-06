package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.NullableOperation
import io.outfoxx.sunday.NullifySpec
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.nullableOperation
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.withSecurity
import io.test.AnotherNotFoundProblem
import io.test.Test
import io.test.TestNotFoundProblem
import kotlin.Int
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmOverloads

public class API<Req : Request> @JvmOverloads constructor(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  private val clientSettings: ClientSettings? = null,
) {
  init {
    transport.registerProblem("http://example.com/test_not_found", TestNotFoundProblem::class)
    transport.registerProblem("http://example.com/another_not_found", AnotherNotFoundProblem::class)
  }
  public fun fetchTest1(limit: Int): NullableOperation<Unit, Test, Req> = (if (clientSettings !=
      null) this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest1")) else
      this.transport).nullableOperation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/test1",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    ),
    NullifySpec(
      statuses = listOf(404, 405),
      problemTypes = listOf(TestNotFoundProblem::class, AnotherNotFoundProblem::class)
    )
  )

  public fun fetchTest2(limit: Int): NullableOperation<Unit, Test, Req> = (if (clientSettings !=
      null) this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest2")) else
      this.transport).nullableOperation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/test2",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    ),
    NullifySpec(
      statuses = listOf(404),
      problemTypes = listOf(TestNotFoundProblem::class, AnotherNotFoundProblem::class)
    )
  )

  public fun fetchTest3(limit: Int): NullableOperation<Unit, Test, Req> = (if (clientSettings !=
      null) this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest3")) else
      this.transport).nullableOperation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/test3",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    ),
    NullifySpec(
      statuses = listOf(),
      problemTypes = listOf(TestNotFoundProblem::class, AnotherNotFoundProblem::class)
    )
  )

  public fun fetchTest4(limit: Int): NullableOperation<Unit, Test, Req> = (if (clientSettings !=
      null) this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest4")) else
      this.transport).nullableOperation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/test4",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    ),
    NullifySpec(
      statuses = listOf(404, 405),
      problemTypes = listOf()
    )
  )

  public fun fetchTest5(limit: Int): NullableOperation<Unit, Test, Req> = (if (clientSettings !=
      null) this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest5")) else
      this.transport).nullableOperation<Unit, Test, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/test5",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    ),
    NullifySpec(
      statuses = listOf(404),
      problemTypes = listOf()
    )
  )
}
