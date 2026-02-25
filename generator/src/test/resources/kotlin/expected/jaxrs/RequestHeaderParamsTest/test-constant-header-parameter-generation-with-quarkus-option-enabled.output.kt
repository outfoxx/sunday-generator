package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.String
import org.jboss.resteasy.reactive.RestHeader
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @PUT
  @Path(value = "/tests")
  public fun putTest(@RestHeader(value = "x-custom") xCustom: String): RestResponse<Test>
}
