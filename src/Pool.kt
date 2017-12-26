package it.menzani.stellarpool

import it.menzani.stellarpool.serialization.Configuration
import it.menzani.stellarpool.serialization.Configuration.Tests.Mode.*
import it.menzani.stellarpool.serialization.ConfigurationFile
import java.sql.Connection
import java.sql.DriverManager
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

val integralFormatter: NumberFormat = NumberFormat.getNumberInstance()
val decimalFormatter: NumberFormat = DecimalFormat.getNumberInstance()

fun main(args: Array<String>) {
    val configuration = ConfigurationFile().open()
    when (configuration.tests.mode) {
        PRODUCTION -> {
            if (args.isEmpty()) {
                println("Arguments: <prize>")
                println("  <prize> — Amount of Lumens to distribute.")
                return
            }
            val pool = Pool(configuration.pool, configuration)
            val prize = StellarCurrency.ofLumens(args[0].toDouble())
            pool.distribute(prize)
        }
        TEST_POOL -> {
            val pool = Pool(configuration.tests.testPool, configuration)
            val prize = StellarCurrency.ofLumens(Math.random() * 10000)
            pool.distribute(prize)
        }
        MAINNET_PAYMENT -> singleMainnetPayment()
    }
}

class Pool(val pool: Account, val configuration: Configuration) {
    val connection: Connection

    init {
        Class.forName("org.postgresql.Driver")
        connection = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/stellar", "postgres", "sKzwnrYLumJA2sHVQ9j7YEMw")
        connection.autoCommit = false
    }

    fun distribute(prize: StellarCurrency) {
        println("============================[ Network info ]============================")
        val accountsCount = countAccounts()
        println("Total number of accounts is $accountsCount")
        val circulatingSupply = circulatingSupply()
        println("These accounts own $circulatingSupply")
        val totalVotes = totalVotes()
        println("Total number of votes is $totalVotes")
        val votersCount = countVoters()
        println("These votes come from $votersCount accounts")

        val minVotes = circulatingSupply / StellarCurrency(2000) // Or multiply by 0.0005
        println("Minimum number of votes is $minVotes")
        println("============================[ Overview ]============================")
        if (totalVotes <= minVotes) {
            println("Threshold has not been reached.")
            return
        }
        println("Distributing $prize")
        println("Paying fees to ${configuration.feeCollector.address}")
        println("============================[ Distribution ]============================")

        var totalRewards = StellarCurrency.ZERO
        for (account in getVoters()) {
            val rewardFactor = account.balance!!.stroops / totalVotes.stroops.toDouble()
            val reward = StellarCurrency((rewardFactor * prize.stroops).roundToLong())

            totalRewards += reward
            if (totalRewards > prize &&
                    totalRewards - prize >= configuration.safetyThresholds.rewardsExceedPrize) {
                println("Attention! Rewards exceeded the prize by at least ${configuration.safetyThresholds.rewardsExceedPrize}")
                return
            }

            val percentOfPrize = Percent(rewardFactor * 100)
            if (reward >= configuration.safetyThresholds.rewardExceedsAmount ||
                    percentOfPrize >= configuration.safetyThresholds.rewardExceedsPercentOfPrize) {
                println("Pay $reward ($percentOfPrize of prize) to $account")
            }

            if (configuration.shouldNotExecutePayments()) {
                continue
            }
            account.pay(reward)
        }

        println("============================[ Summary ]============================")
        println("Sum of actual rewards given is $totalRewards")
        val result = totalRewards.compareTo(prize)
        val resultDescription = when {
            result < 0 -> "lesser than"
            result == 0 -> "equal to"
            result > 0 -> "greater than"
            else -> throw AssertionError()
        }
        val delta = totalRewards.stroops - prize.stroops
        println("It is $resultDescription the prize by ${StellarCurrency(delta.absoluteValue)}")

        if (configuration.shouldNotExecutePayments()) {
            println("It was a NO-OP")
        }
    }

    private fun countAccounts(): Long {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT COUNT(*) FROM accounts")
        assert(results.next(), { "Query returned 0 results." })
        return results.getLong(1)
    }

    private fun countVoters(): Long {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT COUNT(*) FROM accounts WHERE inflationdest='${pool.address}'")
        assert(results.next(), { "Query returned 0 results." })
        return results.getLong(1)
    }

    private fun circulatingSupply(): StellarCurrency {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT SUM(balance) FROM accounts")
        assert(results.next(), { "Query returned 0 results." })
        return StellarCurrency(results.getLong(1))
    }

    private fun totalVotes(): StellarCurrency {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT SUM(balance) FROM accounts WHERE inflationdest='${pool.address}'")
        assert(results.next(), { "Query returned 0 results." })
        return StellarCurrency(results.getLong(1))
    }

    private fun getVoters(): Iterator<Account> {
        val statement = connection.createStatement()
        val results = statement.executeQuery("SELECT accountid, balance FROM accounts WHERE inflationdest='${pool.address}'")
        return object : Iterator<Account> {
            override fun hasNext() = results.next()

            override fun next() = Account(results.getString(1), StellarCurrency(results.getLong(2)))
        }
    }
}

class Account(val address: String, val balance: StellarCurrency? = null) {
    init {
        assert(address.length == 56, { "address must be 56 characters long" })
        assert(address == address.toUpperCase(), { "address must contain uppercase letters only" })
    }

    fun pay(amount: StellarCurrency) {

    }

    override fun toString() = "$address {has $balance}"
}

class StellarCurrency(val stroops: Long) : Comparable<StellarCurrency> {
    val lumensPrecise = stroops / 10_000_000.0
    val lumens = lumensPrecise.roundToLong()

    init {
        assert(stroops >= 0, { "stroops must be greater than or equal to 0" })
    }

    companion object {
        fun ofLumens(lumens: Double) = StellarCurrency((lumens * 10_000_000).roundToLong())

        val ZERO = StellarCurrency(0)
    }

    override fun compareTo(other: StellarCurrency) = stroops.compareTo(other.stroops)

    override fun toString(): String {
        val lumensText = if (lumensPrecise < 1) {
            decimalFormatter.format(lumensPrecise)
        } else {
            integralFormatter.format(lumens)
        }
        return "$lumensText XLM (${integralFormatter.format(stroops)} stroops)"
    }

    operator fun plus(other: StellarCurrency) = StellarCurrency(stroops + other.stroops)
    operator fun minus(other: StellarCurrency) = StellarCurrency(stroops - other.stroops)
    operator fun div(other: StellarCurrency) = StellarCurrency(stroops / other.stroops)
}

class Percent(val value: Double) : Comparable<Percent> {
    val integralValue: Short

    init {
        assert(value in 0..100, { "value must be between 0 and 100 inclusive" })
        integralValue = value.toShort()
    }

    override fun compareTo(other: Percent) = value.compareTo(other.value)

    override fun toString() = "${decimalFormatter.format(value)}%"
}