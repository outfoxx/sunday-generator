package io.test.service

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Any
import kotlin.String
import kotlin.collections.Map
import org.jboss.resteasy.reactive.RestHeader
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(@RestHeader(value = "category") category: FetchTestCategoryHeaderParam,
      @RestHeader(value = "type") type: FetchTestTypeHeaderParam): RestResponse<Map<String, Any>>

  public enum class FetchTestCategoryHeaderParam {
    Politics,
    Science,
  }

  public enum class FetchTestTypeHeaderParam {
    All,
    Limited,
  }
}
