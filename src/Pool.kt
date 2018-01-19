package it.menzani.stellarpool

import it.menzani.stellarpool.Pool.Relation.*
import it.menzani.stellarpool.serialization.ConfigurationFile
import it.menzani.stellarpool.serialization.MalformedConfigurationException
import it.menzani.stellarpool.serialization.pool.Configuration
import it.menzani.stellarpool.serialization.pool.Configuration.Tests.Mode.*
import java.sql.Connection
import java.sql.DriverManager
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

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
                val networkInfo = NetworkInfo(CoreDatabase(configuration.pool, configuration.core))
                horizon.addEndpoint(networkInfo)
                        .addEndpoint(UsageStatistics(horizon, networkInfo))
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

class Pool(private val database: CoreDatabase, private val configuration: Configuration) {
    fun distribute(prize: StellarCurrency, execute: Boolean = false) {
        val formatter = NumberFormat.getNumberInstance()
        println(Header("Network info"))
        val accountsCount = formatter.format(database.countAccounts())
        println("Total number of accounts is $accountsCount")
        val circulatingSupply = database.circulatingSupply()
        println("These accounts own $circulatingSupply")
        val totalVotes = database.totalVotes()
        println("Total number of votes is $totalVotes")
        val votersCount = formatter.format(database.countVoters())
        println("These votes come from $votersCount accounts")

        println(Header("Overview"))
        val minVotes = circulatingSupply / StellarCurrency(2000) // Or multiply by 0.0005
        println("Minimum number of votes is $minVotes")
        if (totalVotes <= minVotes) {
            println("Threshold has not been reached.")
            return
        }
        println("Distributing $prize")

        val paymentExecutor = PaymentExecutor(!execute, configuration.bank, configuration.messages.distribution)
        println(Header("Distribution"))
        var totalRewards = StellarCurrency.ZERO
        var totalActualRewards = StellarCurrency.ZERO
        val fees = TreeMap<Percent, FeeTracker>()
        for (account in database.getVoters()) {
            val rewardFactor = account.balance!!.stroops / totalVotes.stroops.toDouble()
            val reward = StellarCurrency((rewardFactor * prize.stroops).roundToLong())

            totalRewards += reward
            val rewardsComparison: ComparisonResult = totalRewards.compare(prize)
            if (rewardsComparison.relation == GREATER &&
                    rewardsComparison.delta >= configuration.safetyThresholds.rewardsExceedPrize) {
                println("Attention! Sum of all rewards plus fees exceeded the prize by ${rewardsComparison.delta}")
                return
            }

            val fee: Percent = calculateFee(account.balance)
            val feeAmount = StellarCurrency(fee.apply(reward.stroops).roundToLong())
            fees.computeIfAbsent(fee) { FeeTracker() }
                    .add(feeAmount)
            val actualReward = reward - feeAmount
            totalActualRewards += actualReward

            val percentOfPrize = Percent(rewardFactor * 100)
            if (actualReward >= configuration.safetyThresholds.rewardExceedsAmount ||
                    percentOfPrize >= configuration.safetyThresholds.rewardExceedsPercentOfPrize) {
                println("Pay $actualReward ($percentOfPrize of prize) to $account")
            }

            paymentExecutor.pay(account, actualReward)
        }

        println(Header("Fees"))
        val totalFees = totalRewards - totalActualRewards
        println("Total amount of fees taken is $totalFees")
        var recalculatedTotalFees = StellarCurrency.ZERO
        for ((key, value) in fees) {
            println("  $key fee was paid by ${value.counter} accounts, who generated ${value.total}")
            recalculatedTotalFees += value.total
        }

        println(Header("Payment"))
        println("Paying fees to ${configuration.feeCollector}")
        paymentExecutor.pay(configuration.feeCollector, totalFees)
        paymentExecutor.execute()

        println(Header("Summary"))
        println("Total amount of actual rewards given is $totalActualRewards")
        val rewardsComparison: ComparisonResult = totalRewards.compare(prize)
        val rewardsPrefix = if (rewardsComparison.delta >= StellarCurrency(100)) "Attention! " else ""
        println("${rewardsPrefix}Sum of all rewards plus fees is ${rewardsComparison.relation.description} the prize by ${rewardsComparison.delta}")
        val feesComparison: ComparisonResult = totalFees.compare(recalculatedTotalFees)
        val feesPrefix = if (feesComparison.relation == EQUAL) "" else "Attention! "
        println("${feesPrefix}Recalculated total amount of fees is ${feesComparison.relation.description} itself by ${feesComparison.delta}")
    }

    private class Header(private val title: String, private val length: Int = 72) {
        override fun toString(): String {
            val barLength = (length - title.length) / 2
            val bar = Collections.nCopies(barLength, '=').joinToString("")
            return "$bar[ $title ]$bar"
        }
    }

    private fun StellarCurrency.compare(other: StellarCurrency): ComparisonResult {
        val result = this.compareTo(other)
        val relation = when {
            result < 0 -> LESSER
            result == 0 -> EQUAL
            result > 0 -> GREATER
            else -> throw AssertionError()
        }
        val delta = this.stroops - other.stroops
        return ComparisonResult(relation, StellarCurrency(delta.absoluteValue))
    }

    private class ComparisonResult(val relation: Relation, val delta: StellarCurrency)

    private enum class Relation(val description: String) {
        LESSER("lesser than"),
        EQUAL("equal to"),
        GREATER("greater than");
    }

    private fun calculateFee(votes: StellarCurrency): Percent {
        val schedule = configuration.feeSchedule
        schedule.sort()
        var fee: Percent? = null
        for (descriptor in schedule) {
            if (votes < descriptor.threshold) {
                continue
            }
            fee = descriptor.fee
        }
        if (fee == null) throw MalformedConfigurationException("feeSchedule", "- No suitable descriptor found for $votes.")
        return fee
    }

    private class FeeTracker {
        var total = StellarCurrency.ZERO
        var counter = 0

        fun add(amount: StellarCurrency) {
            total += amount
            counter++
        }
    }

    private class PaymentExecutor(private val noop: Boolean, senderSecretSeed: String, memo: String) {
        private val payment = if (noop) null else Payment(ProductionNetwork(), senderSecretSeed, memo)

        fun pay(receiver: Account, amount: StellarCurrency) {
            if (noop) return
            payment!!.addDestination(receiver.address, amount.lumens.toString())
        }

        fun execute() {
            if (noop) {
                println("It was a NO-OP")
                return
            }
            println("Payments are EXECUTED")
            payment!!.send()
        }
    }
}

class Account(val address: String, val balance: StellarCurrency? = null) {
    init {
        if (address.length != 56) throw IllegalArgumentException("address must be 56 characters long")
        if (address != address.toUpperCase()) throw IllegalArgumentException("address must contain uppercase letters only")
    }

    override fun toString(): String {
        return if (balance == null) address else "$address {has $balance}"
    }
}

class StellarCurrency(val stroops: Long) : Comparable<StellarCurrency> {
    companion object {
        val FORMATTER: NumberFormat = DecimalFormat.getNumberInstance()
        val ZERO = StellarCurrency(0)

        init {
            FORMATTER.maximumFractionDigits = 0
        }

        fun ofLumens(lumens: Double) = StellarCurrency((lumens * 10_000_000).roundToLong())
    }

    val lumens = stroops / 10_000_000.0

    init {
        if (stroops < 0) throw IllegalArgumentException("stroops must be greater than or equal to 0")
    }

    override fun compareTo(other: StellarCurrency) = stroops.compareTo(other.stroops)

    override fun toString(): String {
        val lumens: String = FORMATTER.format(lumens)
        val stroops: String = FORMATTER.format(stroops)
        return "$lumens XLM ($stroops stroops)"
    }

    operator fun plus(other: StellarCurrency) = StellarCurrency(stroops + other.stroops)
    operator fun minus(other: StellarCurrency) = StellarCurrency(stroops - other.stroops)
    operator fun div(other: StellarCurrency) = StellarCurrency(stroops / other.stroops)
}

class Percent(private val value: Double) : Comparable<Percent> {
    companion object {
        val FORMATTER: NumberFormat = DecimalFormat.getNumberInstance()
    }

    init {
        if (value !in 0..100) throw IllegalArgumentException("value must be between 0 and 100 inclusive")
    }

    fun apply(value: Long) = (value / 100.0) * this.value

    override fun toString(): String {
        val value: String = FORMATTER.format(value)
        return "$value%"
    }

    override fun compareTo(other: Percent) = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Percent
        return value == other.value
    }

    override fun hashCode() = value.hashCode()
}