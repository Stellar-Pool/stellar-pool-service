package it.menzani.stellarpool

import com.sun.net.httpserver.*
import it.menzani.stellarpool.logging.FileConsumer
import it.menzani.stellarpool.logging.Logger
import it.menzani.stellarpool.logging.SynchronousLogger
import it.menzani.stellarpool.serialization.createJson
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
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class Horizon(private val configuration: Configuration.Horizon) {
    private val log: Logger = SynchronousLogger().addConsumer(FileConsumer(Paths.get("horizon.log")))
    internal val server: HttpServer

    init {
        log.header { "Starting Horizon..." }
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

    fun addEndpoint(endpoint: Endpoint): Horizon {
        val name = '/' + endpoint.name
        log.info { "Registering endpoint: $name" }
        server.createContext(name, Handler(endpoint))
        return this
    }

    fun listen() {
        server.executor = if (configuration.parallelism == 0) Executors.newCachedThreadPool() else
            Executors.newFixedThreadPool(configuration.parallelism)
        server.start()
        log.info { "Horizon has started." }
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
                parameters.put(entry[0], if (entry.size == 1) "" else entry[1])
            }

            log.fine { "Got request from ${exchange.remoteAddress}" }
            val response = doServiceAuthenticated(parameters)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.writeBody(createJson(response))
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

    private inner class Stop : Endpoint {
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
}

class NetworkInfo(private val database: CoreDatabase) : Endpoint {
    internal val totalRequests = AtomicLong()
    internal val totalTime = AtomicLong()

    internal val accountsCount = AtomicLong()
    internal val circulatingSupply = AtomicLong()
    internal val totalVotes = AtomicLong()
    internal val votersCount = AtomicLong()

    override val name = "network"

    override fun service(parameters: Map<String, String>): Any {
        totalRequests.incrementAndGet()
        val profiler = Profiler()
        val result: Any = when (parameters["action"]) {
            "countAccounts" -> {
                accountsCount.incrementAndGet()
                val accountsCount = database.countAccounts()
                accountsCount
            }
            "circulatingSupply" -> {
                circulatingSupply.incrementAndGet()
                val circulatingSupply = database.circulatingSupply()
                Balance.fromCurrency(circulatingSupply)
            }
            "totalVotes" -> {
                totalVotes.incrementAndGet()
                val totalVotes = database.totalVotes()
                Balance.fromCurrency(totalVotes)
            }
            "countVoters" -> {
                votersCount.incrementAndGet()
                val votersCount = database.countVoters()
                votersCount
            }
            null, "" -> return Problem("You must specify an action.")
            else -> return Problem("Invalid action.")
        }
        val time = profiler.report()
        totalTime.addAndGet(time)
        return Ok(QueryResult(result, time, TimeUnit.MILLISECONDS))
    }
}

private class Profiler {
    private val startTime = System.nanoTime()

    fun report(): Long {
        val endTime = System.nanoTime()
        val executionTime = endTime - startTime
        return executionTime / 1_000_000
    }
}

class UsageStatistics(private val horizon: Horizon, private val networkInfo: NetworkInfo) : Endpoint {
    override val name = "usage"

    override fun service(parameters: Map<String, String>): Any {
        val horizonServerExecutor = horizon.server.executor as ThreadPoolExecutor
        return Usage(
                horizonServerExecutor.largestPoolSize,
                Usage.Network(
                        networkInfo.accountsCount.get(),
                        networkInfo.circulatingSupply.get(),
                        networkInfo.totalVotes.get(),
                        networkInfo.votersCount.get(),
                        Usage.Network.Summary(
                                networkInfo.totalRequests.get(),
                                networkInfo.totalTime.get(),
                                TimeUnit.MILLISECONDS)))
    }
}