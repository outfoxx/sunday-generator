package io.test.service

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import java.lang.IllegalArgumentException
import kotlin.Any
import kotlin.String
import kotlin.collections.Map
import kotlin.jvm.JvmStatic
import org.jboss.resteasy.reactive.RestHeader
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(@RestHeader(value = "category") category: FetchTestCategoryHeaderParam,
      @RestHeader(value = "type") type: FetchTestTypeHeaderParam): RestResponse<Map<String, Any>>

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
