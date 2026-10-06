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
  public fun fetchTest(category: FetchTestCategoryQueryParam, type: FetchTestTypeQueryParam):
      Operation<Unit, Map<String, Any>, Req> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest")) else
      this.transport).operation<Unit, Map<String, Any>, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      queryParameters = mapOf(
        "category" to category,
        "type" to type
      ),
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public enum class FetchTestCategoryQueryParam(
    private val wireValue: String,
  ) {
    Politics("politics"),
    Science("science"),
    ;

    public override fun toString(): String = wireValue
  }

  public enum class FetchTestTypeQueryParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    public override fun toString(): String = wireValue
  }
}
