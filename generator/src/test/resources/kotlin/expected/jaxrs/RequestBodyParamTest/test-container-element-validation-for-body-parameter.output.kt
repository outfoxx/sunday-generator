package io.test.service

import io.test.Child
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.collections.List

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @POST
  @Path(value = "/tests")
  public fun fetchTest(body: List<@Valid Child>): Response
}
