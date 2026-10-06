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
import kotlin.Any
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.jvm.JvmOverloads

public class API<Req : Request> @JvmOverloads constructor(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  private val clientSettings: ClientSettings? = null,
) {
  public fun fetchTest(
    obj: Map<String, Any>,
    str: String,
    def: String,
    int: Int,
  ): Operation<Unit, Map<String, Any>, Req> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest")) else
      this.transport).operation<Unit, Map<String, Any>, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests/{obj}/{str}/{int}/{def}",
      pathParameters = mapOf(
        "obj" to obj,
        "str" to str,
        "def" to def,
        "int" to int
      ),
      acceptTypes = this.defaultAcceptTypes
    )
  )
}
