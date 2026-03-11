package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Int
import kotlin.String
import org.jboss.resteasy.reactive.RestHeader
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(
    @RestHeader(value = "obj") obj: Test?,
    @RestHeader(value = "str") str: String?,
    @RestHeader(value = "int") int: Int?,
    @RestHeader(value = "def1") @DefaultValue(value = "test") def1: String,
    @RestHeader(value = "def2") @DefaultValue(value = "10") def2: Int,
  ): RestResponse<Test>
}
