package it.menzani.stellarpool

import java.sql.Connection
import java.sql.DriverManager
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

//val POOL = Account("GAJOC4WSOL3VUHYTEQPSOY54LP3XDWBS3AEZZ4DH24NEOHBBMEQKK7I7") // stellarpool.net
val POOL = Account("GA3FUYFOPWZ25YXTCA73RK2UGONHCO27OHQRSGV3VCE67UEPEFEDCOPA") // xlmpool.com
const val FEE = 5.0
val FEE_COLLECTOR = "GB3BDHHDZLVMI5VXJVCI3736LDTYCUBDN6XOKINSXZGDVW4WDIH4K3AV"
val REWARD_EXCEED_PRIZE_THRESHOLD = StellarCurrency(1.0)

const val LUMEN_EQUALS_STROOPS = 10_000_000
val INTEGRAL_FORMATTER: NumberFormat = NumberFormat.getNumberInstance()
val DECIMAL_FORMATTER: NumberFormat = DecimalFormat.getNumberInstance()

fun main(args: Array<String>) {
    run(args)
}

private fun run(args: Array<String>) {
    val prize: StellarCurrency
    if (args.isEmpty()) {
        println("Syntax: <prize> <bank> [fee] [fee collector]")
        println("  <prize> – Amount of XLM to distribute; should be equal to that given by inflation.")
        println("  <bank> – ID of the account from which the <prize> is withdrawn.")
        println("  [fee] – Fee percentage to apply on each payment.")
        println("  [fee collector] – ID of the account to which fees are paid.")
        return
    }
    val prizeInput = args[0].toDouble()
    prize = StellarCurrency(if (prizeInput < 0) Math.random() * 10000 else prizeInput)
    val feeCollector = Account(if (args.size == 1) FEE_COLLECTOR else args[1])

    Class.forName("org.postgresql.Driver")
    val connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/stellar", "postgres", "sKzwnrYLumJA2sHVQ9j7YEMw")
    connection.autoCommit = false

    println("============================[ General info ]============================")
    val accountsCount = countAccounts(connection)
    println("Total number of accounts is $accountsCount")
    val circulatingSupply = circulatingSupply(connection)
    println("These accounts own $circulatingSupply")
    val totalVotes = totalVotes(connection)
    println("Total number of votes is $totalVotes")
    val votersCount = countVoters(connection)
    println("These votes come from $votersCount accounts")

    val minVotes = StellarCurrency(circulatingSupply.stroops / 2000) // Or multiply by 0.0005
    println("Minimum number of votes is $minVotes")
    println("============================[ Summary ]============================")
    if (totalVotes <= minVotes) {
        println("Threshold has not been reached.")
        return
    }
    println("Distributing $prize")
    println("Paying fees to ${feeCollector.address}")
    println("============================[ Distribution ]============================")

    var totalRewards = 0L
    for (account in getVoters(connection)) {
        val rewardFactor = account.balance!!.stroops / totalVotes.stroops.toDouble()
        val reward = (rewardFactor * prize.stroops).roundToLong()
        account.pay(StellarCurrency(reward), Percent(rewardFactor * 100))

        totalRewards += reward
        if (totalRewards > prize.stroops && StellarCurrency(totalRewards - prize.stroops) >= REWARD_EXCEED_PRIZE_THRESHOLD) {
            println("Attention! Rewards exceeded the prize by at least $REWARD_EXCEED_PRIZE_THRESHOLD")
            return
        }
    }

    println("============================[ Security checks ]============================")
    println("Sum of actual rewards given is ${StellarCurrency(totalRewards)}")
    val delta = totalRewards - prize.stroops
    val deltaDescription = when {
        (delta < 0) -> "lesser than"
        (delta == 0L) -> "equal to"
        else -> "greater than"
    }
    println("It is $deltaDescription the prize by ${StellarCurrency(delta.absoluteValue)}")
}

fun countAccounts(connection: Connection): Long {
    val statement = connection.createStatement()
    val results = statement.executeQuery("SELECT COUNT(*) FROM accounts")
    assert(results.next(), { "Query returned 0 results." })
    return results.getLong(1)
}

fun countVoters(connection: Connection): Long {
    val statement = connection.createStatement()
    val results = statement.executeQuery("SELECT COUNT(*) FROM accounts WHERE inflationdest='${POOL.address}'")
    assert(results.next(), { "Query returned 0 results." })
    return results.getLong(1)
}

fun circulatingSupply(connection: Connection): StellarCurrency {
    val statement = connection.createStatement()
    val results = statement.executeQuery("SELECT SUM(balance) FROM accounts")
    assert(results.next(), { "Query returned 0 results." })
    return StellarCurrency(results.getLong(1))
}

fun totalVotes(connection: Connection): StellarCurrency {
    val statement = connection.createStatement()
    val results = statement.executeQuery("SELECT SUM(balance) FROM accounts WHERE inflationdest='${POOL.address}'")
    assert(results.next(), { "Query returned 0 results." })
    return StellarCurrency(results.getLong(1))
}

fun getVoters(connection: Connection): Iterator<Account> {
    val statement = connection.createStatement()
    val results = statement.executeQuery("SELECT accountid, balance FROM accounts WHERE inflationdest='${POOL.address}'")
    return object : Iterator<Account> {
        override fun hasNext() = results.next()

        override fun next() = Account(results.getString(1), StellarCurrency(results.getLong(2)))
    }
}

class Account(val address: String, val balance: StellarCurrency? = null) {
    init {
        assert(address.length == 56, { "address must be 56 characters long" })
        assert(address == address.toUpperCase(), { "address must contain uppercase letters only" })
    }

    fun pay(amount: StellarCurrency, percentOfPrize: Percent) {
        assert(balance != null, { "Cannot call pay() when balance is null." })

        if (amount >= StellarCurrency(10.0) || percentOfPrize >= Percent(1.0)) {
            println("Pay $amount ($percentOfPrize of prize) to $this")
        }
    }

    override fun toString() = "$address {has $balance}"
}

class StellarCurrency(val stroops: Long) : Comparable<StellarCurrency> {
    val lumensPrecise = stroops / LUMEN_EQUALS_STROOPS.toDouble()
    val lumens = lumensPrecise.roundToLong()

    init {
        assert(stroops >= 0, { "stroops must be greater than or equal to 0" })
    }

    constructor(lumens: Double) : this((lumens * LUMEN_EQUALS_STROOPS).roundToLong())

    override fun equals(other: Any?) = if (other is StellarCurrency) stroops == other.stroops else false

    override fun hashCode(): Int {
        var result = stroops.hashCode()
        result = 31 * result + lumens.hashCode()
        return result
    }

    override fun compareTo(other: StellarCurrency) = stroops.compareTo(other.stroops)

    override fun toString(): String {
        val lumensText = if (lumensPrecise < 1) DECIMAL_FORMATTER.format(lumensPrecise) else INTEGRAL_FORMATTER.format(lumens)
        return "$lumensText XLM (${INTEGRAL_FORMATTER.format(stroops)} stroops)"
    }
}

class Percent(val value: Double) : Comparable<Percent> {
    val integralValue: Short

    init {
        assert(value in 0..100, { "value must be between 0 and 100 inclusive" })
        integralValue = value.toShort()
    }

    override fun compareTo(other: Percent) = value.compareTo(other.value)

    override fun toString() = "${DECIMAL_FORMATTER.format(value)}%"
}