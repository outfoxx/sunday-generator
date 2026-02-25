package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{category}/{type}")
  public fun fetchTest(@PathParam(value = "category") category: FetchTestCategoryUriParam,
      @PathParam(value = "type") type: FetchTestTypeUriParam): Response

  public enum class FetchTestCategoryUriParam {
    Politics,
    Science,
  }

  public enum class FetchTestTypeUriParam {
    All,
    Limited,
  }
}
