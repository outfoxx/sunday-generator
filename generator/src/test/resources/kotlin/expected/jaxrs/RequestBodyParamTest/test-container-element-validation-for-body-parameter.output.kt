package io.test.service

import io.outfoxx.sunday.validation.javax.EntitySchema
import io.outfoxx.sunday.validation.javax.ModelMode
import io.outfoxx.sunday.validation.javax.Schema
import io.test.Child
import io.test.ChildListValidation
import javax.validation.Valid
import javax.validation.groups.ConvertGroup
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.collections.List
import kotlin.jvm.JvmSuppressWildcards

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @POST
  @Path(value = "/tests")
  @EntitySchema(ChildListValidation::class)
  public fun fetchTest(@Schema(requiredValue = true) body: @JvmSuppressWildcards
      List<@Schema(requiredValue = true) @Valid @ConvertGroup(to = ModelMode.Request::class)
      Child>): Response
}
