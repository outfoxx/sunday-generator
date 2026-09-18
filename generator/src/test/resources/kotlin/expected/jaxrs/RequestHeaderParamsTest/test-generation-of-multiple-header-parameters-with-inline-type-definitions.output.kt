package io.test.service

import java.lang.IllegalArgumentException
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.String
import kotlin.jvm.JvmStatic

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(@HeaderParam(value = "category") category: FetchTestCategoryHeaderParam,
      @HeaderParam(value = "type") type: FetchTestTypeHeaderParam): Response

  public enum class FetchTestCategoryHeaderParam(
    private val wireValue: String,
  ) {
    Politics("politics"),
    Science("science"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestCategoryHeaderParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestCategoryHeaderParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestCategoryHeaderParam = fromValue(rawValue)
    }
  }

  public enum class FetchTestTypeHeaderParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestTypeHeaderParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestTypeHeaderParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestTypeHeaderParam = fromValue(rawValue)
    }
  }
}
