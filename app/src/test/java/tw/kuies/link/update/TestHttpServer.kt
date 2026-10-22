package tw.kuies.link.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Minimal real HTTP server for network code; unit tests run on the JVM so this needs no Android. */
class TestHttpServer : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val port: Int get() = server.address.port

    init {
        server.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable).apply { isDaemon = true }
        }
        server.start()
    }

    fun handle(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            try {
                handler(exchange)
            } catch (_: Exception) {
                // The client may have timed out or closed; the assertion is on the client result.
            } finally {
                runCatching { exchange.close() }
            }
        }
    }

    fun bytes(path: String, body: ByteArray) = handle(path) { exchange ->
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    fun text(path: String, body: String) = bytes(path, body.toByteArray())

    fun status(path: String, code: Int, headers: Map<String, String> = emptyMap()) =
        handle(path) { exchange ->
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            exchange.sendResponseHeaders(code, -1)
        }

    fun redirect(path: String, location: String) =
        status(path, 302, mapOf("Location" to location))

    override fun close() {
        server.stop(0)
    }
}

fun localTrust(port: Int): UpdateTrust = UpdateTrust(
    owner = "kuies001",
    repo = "KuiesLink",
    apiBase = "http://127.0.0.1:$port",
    assetBase = "http://127.0.0.1:$port/",
    allowInsecureLocalhost = true,
)

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
