package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response

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

  public enum class FetchTestSelectUriParam {
    All,
    Limited,
  }

  public enum class FetchTestPageQueryParam {
    All,
    Limited,
  }

  public enum class FetchTestXTypeHeaderParam {
    All,
    Limited,
  }
}
