package io.test.service

import java.lang.IllegalArgumentException
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.String
import kotlin.jvm.JvmStatic

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{category}/{type}")
  public fun fetchTest(@PathParam(value = "category") category: FetchTestCategoryUriParam,
      @PathParam(value = "type") type: FetchTestTypeUriParam): Response

  public enum class FetchTestCategoryUriParam(
    private val wireValue: String,
  ) {
    Politics("politics"),
    Science("science"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestCategoryUriParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestCategoryUriParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestCategoryUriParam = fromValue(rawValue)
    }
  }

  public enum class FetchTestTypeUriParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestTypeUriParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestTypeUriParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestTypeUriParam = fromValue(rawValue)
    }
  }
}
