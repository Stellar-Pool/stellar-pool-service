package it.menzani.stellarpool

import it.menzani.stellarpool.serialization.TransactionResult
import it.menzani.stellarpool.serialization.parseJson
import org.stellar.sdk.*
import org.stellar.sdk.responses.AccountResponse
import org.stellar.sdk.responses.SubmitTransactionResponse
import java.net.URL
import java.net.URLEncoder
import java.util.*

fun runInflation(sourceSecretSeed: String, memoText: String) {
    val productionNetwork = ProductionNetwork()
    val executorKeys = KeyPair.fromSecretSeed(sourceSecretSeed)
    val executor: AccountResponse = productionNetwork.accountOf(executorKeys)
    val transaction = Transaction.Builder(executor)
            .addOperation(InflationOperation())
            .addMemo(Memo.text(memoText))
            .build()
    transaction.sign(executorKeys)
    try {
        productionNetwork.makeTransaction(transaction)
        println("Inflation has run.")
    } catch (e: TransactionFailedException) {
        println("Could not make transaction.")
        println(e.result)
    }
}

class Payment(private val network: Network, sourceSecretSeed: String, memoText: String? = null) {
    val sourceKeys: KeyPair = KeyPair.fromSecretSeed(sourceSecretSeed)
    private val sourceAccount: AccountResponse = network.accountOf(sourceKeys)
    private val memo: Memo? = if (memoText != null) Memo.text(memoText) else null
    private val transactionBuilders: Deque<Transaction.Builder> = ArrayDeque<Transaction.Builder>()

    fun addDestination(destinationAccountId: String, amount: String) {
        val destinationKeys = KeyPair.fromAccountId(destinationAccountId)
        val paymentBuilder = PaymentOperation.Builder(destinationKeys, AssetTypeNative(), amount)
        addOperation(paymentBuilder.build())
    }

    private fun addOperation(payment: PaymentOperation) {
        var transactionBuilder = transactionBuilders.peekFirst()
        if (transactionBuilder == null || transactionBuilder.operationsCount == 100) {
            transactionBuilder = Transaction.Builder(sourceAccount)
            if (memo != null) transactionBuilder.addMemo(memo)
            transactionBuilders.addFirst(transactionBuilder)
        }
        transactionBuilder.addOperation(payment)
    }

    fun send() {
        println("Payment has ${transactionBuilders.size} transactions")
        var i = 0
        while (transactionBuilders.isNotEmpty()) {
            val transaction = transactionBuilders.pollLast().build()
            transaction.sign(sourceKeys)
            i++
            try {
                network.makeTransaction(transaction)
                println("  Executed transaction #$i")
            } catch (e: TransactionFailedException) {
                println("  Transaction failed #$i")
            }
        }
    }
}

interface Network {
    fun accountOf(keyPair: KeyPair): AccountResponse
    fun makeTransaction(transaction: Transaction)
}

class TransactionFailedException(val result: Any) : Exception()

class TestNetwork : Network {
    init {
        org.stellar.sdk.Network.useTestNetwork()
    }

    private val server = Server("https://horizon-testnet.stellar.org")

    override fun accountOf(keyPair: KeyPair): AccountResponse = server.accounts().account(keyPair)

    override fun makeTransaction(transaction: Transaction) {
        val submission: SubmitTransactionResponse = server.submitTransaction(transaction)
        if (!submission.isSuccess) throw TransactionFailedException(submission)
    }
}

class ProductionNetwork : Network {
    init {
        org.stellar.sdk.Network.usePublicNetwork()
    }

    private val server = Server("https://horizon.stellar.org")

    override fun accountOf(keyPair: KeyPair): AccountResponse = server.accounts().account(keyPair)

    override fun makeTransaction(transaction: Transaction) {
        val blob = URLEncoder.encode(transaction.toEnvelopeXdrBase64(), "UTF-8")
        val stream = URL("http://localhost:11626/tx?blob=$blob").openStream()
        val result: TransactionResult = parseJson(stream)
        if (result.status != "PENDING") throw TransactionFailedException(result)
    }
}

// Tests
// Can be run in isolation
private fun multipleTestnetPayments() {
    val sourceKeys = KeyPair.fromSecretSeed("SC4TQUMPJKW5S2UPGLPGSHJDTEEGATW2CS4AHXLJM4USUT2UXVUKVZGJ")
    val destinationKeys = KeyPair.fromSecretSeed("SBFJRAEZHMA6GB2OPELLRQVER57V6DLDT6PYJZSJGI4HMZPP7MCJ3QID")
    assert(sourceKeys.accountId == "GCVQMOB6ASUZUJQ3EGQI7WOOIQ6HI6MVPMQCUO5NJYMXIFW3UUF4LM3C", { "sender account keys check failed." })
    assert(destinationKeys.accountId == "GDQSOMO3Z2VPQOCKJI2S5BRSVLHO5F5RN6SXKNFLAMGGXINFNTO4YI36", { "receiver account keys check failed." })

    val test = PaymentTest(TestNetwork())
    test.makePayment(sourceKeys, destinationKeys, 150)
}

fun main(args: Array<String>) {
    multipleTestnetPayments()
}

// Must be executed on the host running Stellar Core
fun singleMainnetPayment(sourceSecretSeed: String, destinationAccountId: String) {
    val sourceKeys = KeyPair.fromSecretSeed(sourceSecretSeed)
    val destinationKeys = KeyPair.fromAccountId(destinationAccountId)

    val test = PaymentTest(ProductionNetwork())
    test.makePayment(sourceKeys, destinationKeys)
}
// =====

class PaymentTest(private val network: Network) {
    fun makePayment(sourceKeys: KeyPair, destinationKeys: KeyPair, repeat: Int = 1) {
        val payment = Payment(network, String(sourceKeys.secretSeed))
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