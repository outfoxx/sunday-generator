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
    type: FetchTestTypeUriParam,
    type_: FetchTestTypeQueryParam,
    type__: FetchTestTypeHeaderParam,
  ): Operation<Unit, Map<String, Any>, Req> = this.transport.operation<Unit, Map<String, Any>, Req>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests/{type}",
      pathParameters = mapOf(
        "type" to type
      ),
      queryParameters = mapOf(
        "type" to type_
      ),
      acceptTypes = this.defaultAcceptTypes,
      headers = mapOf(
        "type" to type__
      )
    )
  )

  public enum class FetchTestTypeUriParam(
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
      public fun fromValue(rawValue: String): FetchTestTypeUriParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestTypeUriParam value: " + rawValue)
      }
    }
  }

  public enum class FetchTestTypeQueryParam(
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
      public fun fromValue(rawValue: String): FetchTestTypeQueryParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestTypeQueryParam value: " + rawValue)
      }
    }
  }

  public enum class FetchTestTypeHeaderParam(
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
      public fun fromValue(rawValue: String): FetchTestTypeHeaderParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestTypeHeaderParam value: " + rawValue)
      }
    }
  }
}
