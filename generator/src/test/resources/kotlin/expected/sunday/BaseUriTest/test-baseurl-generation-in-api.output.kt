package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.security.Credentials
import io.outfoxx.sunday.security.SecurityBinding
import io.outfoxx.sunday.security.TokenManagerFactory
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
public fun <Req : Request> createAPI(
  config: TestAPIConfig,
  transportFactory: (ClientSettings) -> Transport<Req>,
  credentials: APICredentials = APICredentials(),
  securityProfile: String? = null,
  securitySelection: Map<String, APISecurityAlternative> = emptyMap(),
  defaultContentTypes: List<MediaType> = listOf(),
  defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  tokenManagerFactory: TokenManagerFactory? = null,
): API<Req> {
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
  val settings = ClientSettings.resolve(config.baseURL(), selectedAlternatives, supplied,
      tokenManagerFactory = tokenManagerFactory)
  val transport = transportFactory(settings)
  return API(transport, defaultContentTypes, defaultAcceptTypes, settings)
}

public class API<Req : Request> @JvmOverloads constructor(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
  private val clientSettings: ClientSettings? = null,
) {
  public fun fetchTest(): Operation<Unit, String, Req> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchTest")) else
      this.transport).operation<Unit, String, Req>(
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
