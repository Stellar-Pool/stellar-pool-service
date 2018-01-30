package it.menzani.stellarpool

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.*
import it.menzani.stellarpool.distribution.StellarCurrency
import it.menzani.stellarpool.logging.FileConsumer
import it.menzani.stellarpool.logging.Logger
import it.menzani.stellarpool.logging.SynchronousLogger
import it.menzani.stellarpool.serialization.horizon.*
import it.menzani.stellarpool.serialization.pool.Configuration
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Collectors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class Horizon(private val configuration: Configuration.Horizon) {
    private val log: Logger = SynchronousLogger().addConsumer(FileConsumer(Paths.get("horizon.log")))
    private val server: HttpServer
    private val endpoints: MutableList<Endpoint> = mutableListOf() // Ordering is preserved.
    private val mapper = ObjectMapper()

    init {
        log.header { "Loading Horizon..." }
        val address = InetSocketAddress(configuration.port)
        server = if (configuration.certificate.isSslEnabled) {
            log.info { "HTTPS is enabled." }
            val keyStore = KeyStore.getInstance(configuration.certificate.type)
            keyStore.load(Files.newInputStream(configuration.certificate.file), configuration.certificate.password)
            val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
            keyManagerFactory.init(keyStore, configuration.certificate.password)
            val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
            trustManagerFactory.init(keyStore)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
            val server = HttpsServer.create(address, configuration.backlog)
            server.httpsConfigurator = CertificateHandler(sslContext)
            server
        } else {
            HttpServer.create(address, configuration.backlog)
        }
        registerCommonEndpoints()
    }

    private fun registerCommonEndpoints() {
        addEndpoint(Usage())
    }

    fun addEndpoint(endpoint: Endpoint): Horizon {
        log.info { "Registering endpoint: ${endpoint.label()}" }
        server.createContext(endpoint.path(), Handler(endpoint))
        endpoints.add(endpoint)
        return this
    }

    fun listen() {
        server.executor = if (configuration.parallelism == 0) Executors.newCachedThreadPool() else
            Executors.newFixedThreadPool(configuration.parallelism)
        server.start()
        log.info { "Horizon has started." }
    }

    private class CertificateHandler(sslContext: SSLContext) : HttpsConfigurator(sslContext) {
        override fun configure(parameters: HttpsParameters) {
            val sslContext = SSLContext.getDefault()
            val sslEngine = sslContext.createSSLEngine()
            parameters.needClientAuth = false
            parameters.cipherSuites = sslEngine.enabledCipherSuites
            parameters.protocols = sslEngine.enabledProtocols
            parameters.setSSLParameters(sslContext.defaultSSLParameters)
        }
    }

    private inner class Handler(private val endpoint: Endpoint) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                doHandle(exchange)
            } catch (e: Throwable) {
                val message = "An error occurred."
                log.throwable(e, { message })
                exchange.writeBody(message)
            }
        }

        private fun doHandle(exchange: HttpExchange) {
            val query: String? = exchange.requestURI.query
            val parameters: MutableMap<String, String> = mutableMapOf()
            for (pair in query?.split('&') ?: emptyList()) {
                val entry = pair.split('=')
                parameters[entry[0]] = if (entry.size == 1) "" else entry[1]
            }

            log.fine { "Got request for ${endpoint.label()} from ${exchange.remoteAddress}" }
            val response = doServiceAuthenticated(parameters)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.writeBody(mapper.writeValueAsString(response))
            exchange.close()
        }

        private fun HttpExchange.writeBody(body: String) {
            val bytes = body.toByteArray()
            this.sendResponseHeaders(200, bytes.size.toLong())
            this.responseBody.use {
                it.write(bytes)
            }
        }

        private fun doServiceAuthenticated(parameters: Map<String, String>): Any {
            if (configuration.password.isEmpty()) {
                return endpoint.service(parameters)
            }
            if ("password" !in parameters) return Problem("You must specify a password.")
            if (parameters["password"] == configuration.password) return try {
                endpoint.service(parameters)
            } catch (e: Throwable) {
                val message = "An error occurred while calculating the response."
                log.throwable(e, { message })
                Problem(message)
            }
            val message = "Authentication failed."
            log.warn { message }
            return Problem(message)
        }
    }

    private interface CommonEndpoint : Endpoint {
        override fun label() = super.label() + " [Built-in]"
    }

    private inner class Usage : CommonEndpoint {
        private val endpoints by lazy {
            this@Horizon.endpoints.filterIsInstance<RequestTrackingEndpoint>()
        }
        override val name = "usage"

        override fun service(parameters: Map<String, String>): Any {
            val executor = server.executor as ThreadPoolExecutor
            return Usage(executor.largestPoolSize, endpoints.stream()
                    .map { endpoint -> it.menzani.stellarpool.serialization.horizon.Usage.Endpoint(endpoint.name, endpoint.usageDescriptor()) }
                    .collect(Collectors.toList()))
        }
    }

    private inner class Stop : CommonEndpoint {
        override val name = "stop"

        override fun service(parameters: Map<String, String>): Any {
            val message = "Stopping..."
            log.info { message }
            val executor = server.executor as ExecutorService
            executor.execute({
                server.stop(5)
                log.info { "Horizon has stopped." }
            })
            executor.shutdown()
            return Ok(message)
        }
    }
}

interface Endpoint {
    val name: String
    fun service(parameters: Map<String, String>): Any

    fun path() = '/' + name

    /**
     * Equivalent to `toString()`.
     */
    fun label() = path()
}

abstract class RequestTrackingEndpoint : Endpoint {
    private val totalRequests = AtomicLong()

    override fun service(parameters: Map<String, String>): Any {
        totalRequests.incrementAndGet()
        return doService(parameters)
    }

    abstract fun doService(parameters: Map<String, String>): Any

    open fun usageDescriptor() = Usage.Endpoint.Descriptor(totalRequests.get())
}

abstract class ProfiledEndpoint : RequestTrackingEndpoint() {
    private val totalTime = AtomicLong()

    override fun service(parameters: Map<String, String>): Any {
        val profiler = Profiler()
        val result: Any = super.service(parameters)
        val executionTime = profiler.report()
        totalTime.addAndGet(executionTime)

        return result as? Problem ?: Ok(ProfiledEndpointResult(result, executionTime, TimeUnit.MILLISECONDS))
    }

    abstract override fun doService(parameters: Map<String, String>): Any

    override fun usageDescriptor(): Usage.Endpoint.Descriptor = super.usageDescriptor().setTotalTime(totalTime.get(), TimeUnit.MILLISECONDS)
}

private class Profiler {
    private val startTime = System.nanoTime()

    fun report(): Long {
        val endTime = System.nanoTime()
        val executionTime = endTime - startTime
        return executionTime / 1_000_000
    }
}

class AccountsCount(private val database: CoreDatabase) : ProfiledEndpoint() {
    override val name = "network/accounts-count"

    override fun doService(parameters: Map<String, String>) = database.countAccounts()
}

class CirculatingSupply(private val database: CoreDatabase) : ProfiledEndpoint() {
    override val name = "network/circulating-supply"

    override fun doService(parameters: Map<String, String>) = Balance.fromCurrency(database.circulatingSupply())
}

class TotalVotes(private val database: CoreDatabase) : ProfiledEndpoint() {
    override val name = "network/total-votes"

    override fun doService(parameters: Map<String, String>) = Balance.fromCurrency(database.totalVotes())
}

class VotersCount(private val database: CoreDatabase) : ProfiledEndpoint() {
    override val name = "network/voters-count"

    override fun doService(parameters: Map<String, String>) = database.countVoters()
}

class MinimumVotes(private val database: CoreDatabase) : ProfiledEndpoint() {
    override val name = "overview/minimum-votes"

    override fun doService(parameters: Map<String, String>) = Balance.fromCurrency(
            database.circulatingSupply() / StellarCurrency(2000))
}