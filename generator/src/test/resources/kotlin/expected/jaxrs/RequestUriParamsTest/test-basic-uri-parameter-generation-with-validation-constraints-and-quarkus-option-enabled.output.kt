package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import javax.validation.Valid
import kotlin.Int
import kotlin.String
import org.jboss.resteasy.reactive.RestPath
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{obj}/{str-req}/{int}/{def}")
  public fun fetchTest(
    @RestPath def: String,
    @RestPath @Valid obj: Test,
    @RestPath strReq: String,
    @RestPath @DefaultValue(value = "5") int: Int,
  ): RestResponse<Test>
}
