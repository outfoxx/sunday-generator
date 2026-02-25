package io.test.service

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Any
import kotlin.String
import kotlin.collections.Map
import org.jboss.resteasy.reactive.RestQuery
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(@RestQuery category: FetchTestCategoryQueryParam, @RestQuery
      type: FetchTestTypeQueryParam): RestResponse<Map<String, Any>>

  public enum class FetchTestCategoryQueryParam {
    Politics,
    Science,
  }

  public enum class FetchTestTypeQueryParam {
    All,
    Limited,
  }
}
