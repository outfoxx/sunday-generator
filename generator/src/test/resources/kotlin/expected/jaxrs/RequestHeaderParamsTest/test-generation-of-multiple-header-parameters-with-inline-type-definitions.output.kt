package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(@HeaderParam(value = "category") category: FetchTestCategoryHeaderParam,
      @HeaderParam(value = "type") type: FetchTestTypeHeaderParam): Response

  public enum class FetchTestCategoryHeaderParam {
    Politics,
    Science,
  }

  public enum class FetchTestTypeHeaderParam {
    All,
    Limited,
  }
}
