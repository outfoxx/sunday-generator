package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.operation
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.security.Credentials
import io.outfoxx.sunday.security.SecurityBinding
import io.outfoxx.sunday.withSecurity
import io.test.Environment
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.jvm.JvmOverloads

/**
 * Creates API using the application's chosen transport. The caller owns its lifecycle.
 */
public fun <TReq, TRes> createAPI(
  config: TestAPIConfig,
  transportFactory: (ClientSettings) -> Transport<TReq, TRes>,
  credentials: APICredentials = APICredentials(),
  securityProfile: String? = null,
  securitySelection: Map<String, APISecurityAlternative> = emptyMap(),
  defaultContentTypes: List<MediaType> = listOf(),
  defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
): API<TReq, TRes> {
  val supplied = mutableMapOf<String, Credentials>()
  val alternatives: Map<String, List<List<SecurityBinding>>> = when (securityProfile) {
    null -> mapOf(
      "fetchTest" to listOf(emptyList()),
    )
    else -> error("Unknown client security profile")
  }
  require(securitySelection.keys.all { it in alternatives }) {
      "Unknown operation in security selection" }
  val selectedAlternatives = alternatives.mapValues { (operation, choices) -> choices.filter {
      securitySelection[operation]?.matches(it) ?: true } }
  val settings = ClientSettings.resolve(config.baseURL(), selectedAlternatives, supplied)
  val transport = transportFactory(settings)
  return API(transport, defaultContentTypes, defaultAcceptTypes, settings)
}

public class API<TReq, TRes> @JvmOverloads constructor(
  public val transport: Transport<TReq, TRes>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  private val clientSettings: ClientSettings? = null,
) {
  public fun fetchTest(): Operation<Unit, String, TReq, TRes> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest")) else
      this.transport).operation<Unit, String, TReq, TRes>(
    OperationSpec(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )
  )

  public companion object {
    public fun baseURL(
      server: String? = "master",
      environment: Environment? = Environment.Sbx,
      version: String? = "1",
    ): URITemplate = URITemplate(
      "http://{server}.{environment}.example.com/api/{version}",
      mapOf("server" to server, "environment" to environment, "version" to version)
    )
  }
}
