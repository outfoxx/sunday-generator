package io.test.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.test.InvalidIdProblem
import io.test.TestNotFoundProblem
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
  public fun fetchTest(): Response

  public companion object {
    public fun registerProblems(mapper: ObjectMapper) {
      mapper.registerSubtypes(
        InvalidIdProblem::class.java,
        TestNotFoundProblem::class.java
      )
    }
  }
}
