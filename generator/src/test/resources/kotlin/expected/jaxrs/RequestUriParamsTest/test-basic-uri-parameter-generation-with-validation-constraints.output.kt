package io.test.service

import io.outfoxx.sunday.validation.javax.CascadedValues
import io.outfoxx.sunday.validation.javax.EntitySchema
import io.outfoxx.sunday.validation.javax.ModelMode
import io.test.Test
import javax.ws.rs.Consumes
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.Int
import kotlin.String

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{obj}/{str-req}/{int}/{def}")
  @EntitySchema
  public fun fetchTest(
    @PathParam(value = "def") def: String,
    @PathParam(value = "obj") @CascadedValues(mode = ModelMode.Request::class) obj: Test,
    @PathParam(value = "str-req") strReq: String,
    @PathParam(value = "int") @DefaultValue(value = "5") int: Int,
  ): Response
}
