package it.menzani.stellarpool

import it.menzani.stellarpool.serialization.TransactionResult
import it.menzani.stellarpool.serialization.parseJson
import org.stellar.sdk.*
import org.stellar.sdk.responses.AccountResponse
import org.stellar.sdk.responses.SubmitTransactionResponse
import java.net.URL
import java.net.URLEncoder
import java.util.*

fun main(args: Array<String>) {
    multipleTestnetPayments()
}

// Can be executed locally
private fun multipleTestnetPayments() {
    val testNetwork = TestNetwork()

    val payment = Payment(testNetwork, String(testNetwork.sender.secretSeed))
    for (i in 1..150) {
        payment.addDestination(testNetwork.receiver.accountId, "0.0000001")
    }

    testNetwork.printBalances()
    payment.send()
    println("Wait 10 seconds...")
    Thread.sleep(10000)
    testNetwork.printBalances()
}

// Must be executed on the host running Stellar Core
fun runInflation(sourceSecretSeed: String, memoText: String) {
    val productionNetwork = ProductionNetwork()
    val executorKeys = KeyPair.fromSecretSeed(sourceSecretSeed)
    val executor: AccountResponse = productionNetwork.accountOf(executorKeys)
    val transaction = Transaction.Builder(executor)
            .addOperation(InflationOperation())
            .addMemo(Memo.text(memoText))
            .build()
    transaction.sign(executorKeys)
    productionNetwork.makeTransaction(transaction)
}

fun singleMainnetPayment(sourceSecretSeed: String, destinationAccountId: String) {
    val productionNetwork = ProductionNetwork()

    val payment = Payment(productionNetwork, sourceSecretSeed)
    val destination = KeyPair.fromAccountId(destinationAccountId)
    payment.addDestination(destination.accountId, "0.0000001")

    print("Destination account balance is ")
    productionNetwork.printBalance(destination)
    payment.send()
    println("Wait 10 seconds...")
    Thread.sleep(10000)
    print("Destination account balance is ")
    productionNetwork.printBalance(destination)
}
// ===================================================

class Payment(val network: Network, sourceSecretSeed: String, memoText: String? = null) {
    val sourceKeys: KeyPair = KeyPair.fromSecretSeed(sourceSecretSeed)
    val sourceAccount: AccountResponse = network.accountOf(sourceKeys)
    val memo: Memo? = if (memoText != null) Memo.text(memoText) else null
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
        var i = 1
        while (transactionBuilders.isNotEmpty()) {
            val transaction = transactionBuilders.pollLast().build()
            transaction.sign(sourceKeys)
            network.makeTransaction(transaction)
            println("  Executed transaction #${i++}")
        }
    }
}

interface Network {
    fun accountOf(keyPair: KeyPair): AccountResponse
    fun makeTransaction(transaction: Transaction)

    fun printBalance(keyPair: KeyPair) {
        val account: AccountResponse = accountOf(keyPair)
        for (balance in account.balances) {
            println("{Type: ${balance.assetType}, Code: ${balance.assetCode}, Balance: ${balance.balance}}")
        }
    }
}

class TestNetwork : Network {
    init {
        org.stellar.sdk.Network.useTestNetwork()
    }

    val server = Server("https://horizon-testnet.stellar.org")

    override fun accountOf(keyPair: KeyPair): AccountResponse = server.accounts().account(keyPair)

    override fun makeTransaction(transaction: Transaction) {
        val submission: SubmitTransactionResponse = server.submitTransaction(transaction)
        assert(submission.isSuccess, { "Transaction failed." })
    }

    // Test utilities
    val sender: KeyPair = KeyPair.fromSecretSeed("SBFJRAEZHMA6GB2OPELLRQVER57V6DLDT6PYJZSJGI4HMZPP7MCJ3QID")
    val receiver: KeyPair = KeyPair.fromSecretSeed("SC4TQUMPJKW5S2UPGLPGSHJDTEEGATW2CS4AHXLJM4USUT2UXVUKVZGJ")

    init {
        assert(sender.accountId == "GDQSOMO3Z2VPQOCKJI2S5BRSVLHO5F5RN6SXKNFLAMGGXINFNTO4YI36")
        assert(receiver.accountId == "GCVQMOB6ASUZUJQ3EGQI7WOOIQ6HI6MVPMQCUO5NJYMXIFW3UUF4LM3C")
    }

    fun printBalances() {
        print("Sender account balance is ")
        printBalance(sender)
        print("Receiver account balance is ")
        printBalance(receiver)
    }
    // ==============
}

class ProductionNetwork : Network {
    init {
        org.stellar.sdk.Network.usePublicNetwork()
    }

    val server = Server("https://horizon.stellar.org")

    override fun accountOf(keyPair: KeyPair): AccountResponse = server.accounts().account(keyPair)

    override fun makeTransaction(transaction: Transaction) {
        val blob = URLEncoder.encode(transaction.toEnvelopeXdrBase64(), "UTF-8")
        val stream = URL("http://localhost:11626/tx?blob=$blob").openStream()
        val result = parseJson<TransactionResult>(stream)
        assert(result.status == "PENDING", { "Transaction failed." })
    }
}