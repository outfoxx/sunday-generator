package io.test.service

import io.test.Base
import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public suspend fun fetchTest(): RestResponse<Test>

  @GET
  @Path(value = "/tests/derived")
  public suspend fun fetchDerivedTest(): RestResponse<Base>
}
