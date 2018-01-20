package it.menzani.stellarpool.distribution

import it.menzani.stellarpool.distribution.TestAccounts.destinationKeys
import it.menzani.stellarpool.distribution.TestAccounts.sourceKeys
import org.stellar.sdk.KeyPair
import org.stellar.sdk.responses.AccountResponse

private fun runInflation() {
    val inflation = Inflation(TestNetwork())
    inflation.run(sourceKeys.secretSeedString)
}

private fun multipleTestnetPayments() {
    val test = PaymentTest(TestNetwork())
    test.makePayment(sourceKeys, destinationKeys, 150)
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Arguments: <function>")
        println("  <function> – Name of test function to run.")
        return
    }
    when (args[0]) {
        "runInflation" -> runInflation()
        "multipleTestnetPayments" -> multipleTestnetPayments()
        else -> println("Invalid function name.")
    }
}

fun singleMainnetPayment(sourceSecretSeed: String, destinationAccountId: String) {
    val sourceKeys = KeyPair.fromSecretSeed(sourceSecretSeed)
    val destinationKeys = KeyPair.fromAccountId(destinationAccountId)

    val test = PaymentTest(ProductionNetwork())
    test.makePayment(sourceKeys, destinationKeys)
}

object TestAccounts {
    val sourceKeys: KeyPair = KeyPair.fromSecretSeed("SC4TQUMPJKW5S2UPGLPGSHJDTEEGATW2CS4AHXLJM4USUT2UXVUKVZGJ")
    val destinationKeys: KeyPair = KeyPair.fromSecretSeed("SBFJRAEZHMA6GB2OPELLRQVER57V6DLDT6PYJZSJGI4HMZPP7MCJ3QID")

    init {
        assert(sourceKeys.accountId == "GCVQMOB6ASUZUJQ3EGQI7WOOIQ6HI6MVPMQCUO5NJYMXIFW3UUF4LM3C", { "Source account keys check failed." })
        assert(destinationKeys.accountId == "GDQSOMO3Z2VPQOCKJI2S5BRSVLHO5F5RN6SXKNFLAMGGXINFNTO4YI36", { "Destination account keys check failed." })
    }
}

private val KeyPair.secretSeedString
    get() = String(this.secretSeed)

class PaymentTest(private val network: Network) {
    fun makePayment(sourceKeys: KeyPair, destinationKeys: KeyPair, repeat: Int = 1) {
        val payment = Payment(network, sourceKeys.secretSeedString)
        for (i in 1..repeat) {
            payment.addDestination(destinationKeys.accountId, "0.0000001")
        }
        val runnable = Runner(payment, destinationKeys)
        Thread(runnable).start()
    }

    private inner class Runner(private val payment: Payment, private val destinationKeys: KeyPair) : Runnable {
        override fun run() {
            printBalances()
            payment.send()
            println("Wait 10 seconds...")
            Thread.sleep(10000)
            printBalances()
        }

        private fun printBalances() {
            print("Sender account balance is ")
            printBalance(payment.sourceKeys)
            print("Receiver account balance is ")
            printBalance(destinationKeys)
        }

        private fun printBalance(keyPair: KeyPair) {
            val account: AccountResponse = network.accountOf(keyPair)
            for (balance in account.balances) {
                println("{Type: ${balance.assetType}, Code: ${balance.assetCode}, Balance: ${balance.balance}}")
            }
        }
    }
}