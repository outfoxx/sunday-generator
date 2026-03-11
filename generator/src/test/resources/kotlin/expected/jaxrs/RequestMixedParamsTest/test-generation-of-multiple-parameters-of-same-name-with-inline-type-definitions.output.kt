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
  @Path(value = "/tests/{type}")
  public fun fetchTest(
    @PathParam(value = "type") type: FetchTestTypeUriParam,
    @QueryParam(value = "type") type_: FetchTestTypeQueryParam,
    @HeaderParam(value = "type") type__: FetchTestTypeHeaderParam,
  ): Response

  public enum class FetchTestTypeUriParam {
    All,
    Limited,
  }

  public enum class FetchTestTypeQueryParam {
    All,
    Limited,
  }

  public enum class FetchTestTypeHeaderParam {
    All,
    Limited,
  }
}
