package io.test.service

import com.fasterxml.jackson.`annotation`.JsonCreator
import com.fasterxml.jackson.`annotation`.JsonValue
import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import java.lang.IllegalArgumentException
import kotlin.Any
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.jvm.JvmStatic

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public fun fetchTest(
    select: FetchTestSelectUriParam,
    page: FetchTestPageQueryParam,
    xType: FetchTestXTypeHeaderParam,
  ): Operation<Unit, Map<String, Any>, Req> = this.transport.operation<Unit, Map<String, Any>, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests/{select}",
      pathParameters = mapOf(
        "select" to select
      ),
      queryParameters = mapOf(
        "page" to page
      ),
      acceptTypes = this.defaultAcceptTypes,
      headers = mapOf(
        "x-type" to xType
      )
    )
  )

  public enum class FetchTestSelectUriParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    @JsonValue
    public override fun toString(): String = wireValue

    public companion object {
      @JsonCreator
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestSelectUriParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestSelectUriParam value: " + rawValue)
      }
    }
  }

  public enum class FetchTestPageQueryParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    @JsonValue
    public override fun toString(): String = wireValue

    public companion object {
      @JsonCreator
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestPageQueryParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestPageQueryParam value: " + rawValue)
      }
    }
  }

  public enum class FetchTestXTypeHeaderParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    @JsonValue
    public override fun toString(): String = wireValue

    public companion object {
      @JsonCreator
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestXTypeHeaderParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestXTypeHeaderParam value: " + rawValue)
      }
    }
  }
}
