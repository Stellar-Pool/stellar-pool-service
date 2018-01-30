package it.menzani.stellarpool

import it.menzani.stellarpool.distribution.*
import it.menzani.stellarpool.serialization.ConfigurationFile
import it.menzani.stellarpool.serialization.pool.Configuration
import it.menzani.stellarpool.serialization.pool.Configuration.Tests.Mode.*
import java.sql.Connection
import java.sql.DriverManager

fun main(args: Array<String>) {
    val configuration = ConfigurationFile().open()
    if (args.isNotEmpty()) {
        when (args[0]) {
            "--run-inflation" -> {
                val inflation = Inflation(ProductionNetwork(), configuration.messages.inflation)
                inflation.run(configuration.bank)
                return
            }
            "--start-horizon" -> {
                val horizon = Horizon(configuration.horizon)
                val database = CoreDatabase(configuration.pool, configuration.core)
                horizon.addEndpoint(AccountsCount(database))
                        .addEndpoint(CirculatingSupply(database))
                        .addEndpoint(TotalVotes(database))
                        .addEndpoint(VotersCount(database))
                        .addEndpoint(MinimumVotes(database))
                        .listen()
                return
            }
        }
    }
    when (configuration.tests.mode) {
        PRODUCTION -> {
            if (args.isEmpty()) {
                println("Arguments: <prize>")
                println("  <prize> — Amount of Lumens to distribute.")
                println("  ['--execute'] — If unset, gets an overview of what would be done, without actually executing payments.")
                return
            }
            val pool = Pool(CoreDatabase(configuration.pool, configuration.core), configuration)
            val prize = StellarCurrency.ofLumens(args[0].toDouble())
            val execute = args.size != 1 && args[1] == "--execute"
            pool.distribute(prize, execute)
        }
        TEST_POOL -> {
            val pool = Pool(CoreDatabase(configuration.tests.testPool, configuration.core), configuration)
            val prize = StellarCurrency.ofLumens(Math.random() * 10000)
            pool.distribute(prize)
        }
        MAINNET_PAYMENT -> {
            if (args.size < 2) {
                println("Arguments: <source> <destination>")
                println("  <source> – Secret seed of the account that should make the payment.")
                println("  <destination> – ID of the account to which the payment should be made.")
                return
            }
            singleMainnetPayment(args[0], args[1])
        }
    }
}

class CoreDatabase(private val pool: Account, configuration: Configuration.Core) {
    private val connection: Connection

    init {
        Class.forName("org.postgresql.Driver")
        connection = DriverManager.getConnection(
                "jdbc:postgresql://${configuration.host}:5432/${configuration.database}", configuration.user, configuration.password)
        connection.autoCommit = false
    }

    fun countAccounts(): Long {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT COUNT(*) FROM accounts")
        assert(results.next(), { "Query returned no results." })
        return results.getLong(1)
    }

    fun countVoters(): Long {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT COUNT(*) FROM accounts WHERE inflationdest='${pool.address}'")
        assert(results.next(), { "Query returned no results." })
        return results.getLong(1)
    }

    fun circulatingSupply(): StellarCurrency {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT SUM(balance) FROM accounts")
        assert(results.next(), { "Query returned no results." })
        return StellarCurrency(results.getLong(1))
    }

    fun totalVotes(): StellarCurrency {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT SUM(balance) FROM accounts WHERE inflationdest='${pool.address}'")
        assert(results.next(), { "Query returned no results." })
        return StellarCurrency(results.getLong(1))
    }

    fun getVoters(): Iterator<Account> {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT accountid, balance FROM accounts WHERE inflationdest='${pool.address}'")
        return object : Iterator<Account> {
            override fun hasNext() = results.next()

            override fun next() = Account(results.getString(1), StellarCurrency(results.getLong(2)))
        }
    }
}