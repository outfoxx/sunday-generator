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
import org.jboss.resteasy.reactive.RestQuery
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(@RestQuery category: FetchTestCategoryQueryParam, @RestQuery
      type: FetchTestTypeQueryParam): RestResponse<Map<String, Any>>

  public enum class FetchTestCategoryQueryParam(
    private val wireValue: String,
  ) {
    Politics("politics"),
    Science("science"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestCategoryQueryParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestCategoryQueryParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestCategoryQueryParam = fromValue(rawValue)
    }
  }

  public enum class FetchTestTypeQueryParam(
    private val wireValue: String,
  ) {
    All("all"),
    Limited("limited"),
    ;

    public override fun toString(): String = wireValue

    public companion object {
      @JvmStatic
      public fun fromValue(rawValue: String): FetchTestTypeQueryParam {
        for (entry in entries) {
          if (entry.wireValue == rawValue) {
            return entry
          }
        }
        throw IllegalArgumentException("Unknown FetchTestTypeQueryParam value: " + rawValue)
      }

      /**
       * Converts a REST parameter from its declared wire value.
       */
      @JvmStatic
      public fun fromString(rawValue: String): FetchTestTypeQueryParam = fromValue(rawValue)
    }
  }
}
