package io.test.service

import io.outfoxx.sunday.validation.javax.EntitySchema
import io.outfoxx.sunday.validation.javax.ModelMode
import io.outfoxx.sunday.validation.javax.Schema
import io.test.Test
import javax.validation.Valid
import javax.validation.groups.ConvertGroup
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  @EntitySchema
  public fun fetchTest(@Schema(requiredValue = true) @Valid @ConvertGroup(to =
      ModelMode.Request::class) body: Test): Response
}
