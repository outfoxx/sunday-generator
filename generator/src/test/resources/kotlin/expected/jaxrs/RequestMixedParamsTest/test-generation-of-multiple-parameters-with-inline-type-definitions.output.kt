package io.test.service

import java.lang.IllegalArgumentException
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response
import kotlin.String
import kotlin.jvm.JvmStatic

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{select}")
  public fun fetchTest(
    @PathParam(value = "select") select: FetchTestSelectUriParam,
    @QueryParam(value = "page") page: FetchTestPageQueryParam,
    @HeaderParam(value = "x-type") xType: FetchTestXTypeHeaderParam,
  ): Response

  public enum class FetchTestSelectUriParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestSelectUriParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestSelectUriParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestSelectUriParam = fromValue(rawValue)
    }
  }

  public enum class FetchTestPageQueryParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestPageQueryParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestPageQueryParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestPageQueryParam = fromValue(rawValue)
    }
  }

  public enum class FetchTestXTypeHeaderParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestXTypeHeaderParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestXTypeHeaderParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestXTypeHeaderParam = fromValue(rawValue)
    }
  }
}
