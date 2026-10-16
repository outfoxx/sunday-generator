package example

import com.sun.net.httpserver.HttpServer
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.jdk.JdkTransport
import io.outfoxx.sunday.problems.SundayHttpProblem
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

/** Executes generated download operations against real HTTP responses. */
object BinaryCheck {
  /** Entry point called after compiling generated clients. */
  @JvmStatic
  fun verify() = runBlocking {
    var media = "application/json"
    var body = byteArrayOf(0, -1, 34, -128)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      exchange.responseHeaders.add("Content-Type", media)
      exchange.sendResponseHeaders(200, if (body.isEmpty()) -1 else body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.start()
    try {
      JdkTransport(URITemplate("http://127.0.0.1:${server.address.port}"), SundayHttpProblem.Factory).use { transport ->
        val client = DownloadsAPI(transport)
        for (contentType in listOf("application/json", "text/plain; charset=iso-8859-1", "image/png", "application/x-download")) {
          media = contentType
          for (operation in listOf(client.directDownload(), client.aliasDownload(), client.unionDownload(), client.nestedDownload())) {
            check(operation.execute().contentEquals(body)) { "Download bytes changed: $media" }
            check(operation.response().result.contentEquals(body))
          }
        }
        media = "application/json"
        body = "\"asset\"".toByteArray()
        check(client.mixedDownload().execute() == "asset")
        body = byteArrayOf()
        val failure = runCatching { client.directDownload().execute() }.exceptionOrNull()
        check(failure is io.outfoxx.sunday.SundayError && failure.reason == io.outfoxx.sunday.SundayError.Reason.NoData) { "The Kotlin runtime must reject an empty typed response" }
      }
    } finally {
      server.stop(0)
    }
  }
}
