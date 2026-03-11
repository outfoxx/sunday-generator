package io.test.service

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Any
import kotlin.String
import kotlin.collections.Map
import org.jboss.resteasy.reactive.RestPath
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{category}/{type}")
  public fun fetchTest(@RestPath category: FetchTestCategoryUriParam, @RestPath
      type: FetchTestTypeUriParam): RestResponse<Map<String, Any>>

  public enum class FetchTestCategoryUriParam {
    Politics,
    Science,
  }

  public enum class FetchTestTypeUriParam {
    All,
    Limited,
  }
}
