package io.test.service

import io.outfoxx.sunday.validation.jakarta.EntitySchema
import io.outfoxx.sunday.validation.jakarta.ModelMode
import io.outfoxx.sunday.validation.jakarta.Schema
import io.test.Test
import jakarta.validation.Valid
import jakarta.validation.groups.ConvertGroup
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
  @EntitySchema
  public fun fetchTest(@Schema(requiredValue = true) @Valid @ConvertGroup(to =
      ModelMode.Request::class) body: Test): RestResponse<Test>
}
