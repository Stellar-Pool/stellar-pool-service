package it.menzani.stellarpool.distribution

import it.menzani.stellarpool.serialization.parseJson
import it.menzani.stellarpool.serialization.pool.TransactionResult
import org.stellar.sdk.*
import org.stellar.sdk.responses.AccountResponse
import org.stellar.sdk.responses.SubmitTransactionResponse
import java.net.ConnectException
import java.net.URL
import java.net.URLEncoder
import java.util.*

class Inflation(private val network: Network, private val memo: String? = null) {
    fun run(executorSecretSeed: String) {
        val executorKeys = KeyPair.fromSecretSeed(executorSecretSeed)
        val executor: AccountResponse = network.accountOf(executorKeys)
        val transaction = Transaction.Builder(executor)
                .addOperation(InflationOperation())
                .addMemoIfPresent(memo)
                .build()
        transaction.sign(executorKeys)
        try {
            network.makeTransaction(transaction)
            println("Inflation has run.")
        } catch (e: TransactionFailedException) {
            print("Could not make transaction: ")
            println(e.result)
        }
    }
}

class Payment(private val network: Network, sourceSecretSeed: String, private val memo: String? = null) {
    val sourceKeys: KeyPair = KeyPair.fromSecretSeed(sourceSecretSeed)
    private val sourceAccount: AccountResponse = network.accountOf(sourceKeys)
    private val transactionBuilders: Deque<Transaction.Builder> = ArrayDeque<Transaction.Builder>()

    fun addDestination(destinationAccountId: String, amount: String) {
        val destinationKeys = KeyPair.fromAccountId(destinationAccountId)
        val paymentBuilder = PaymentOperation.Builder(destinationKeys, AssetTypeNative(), amount)
        addOperation(paymentBuilder.build())
    }

    private fun addOperation(payment: PaymentOperation) {
        var transactionBuilder = transactionBuilders.peekFirst()
        if (transactionBuilder == null || transactionBuilder.operationsCount == 100) {
            transactionBuilder = Transaction.Builder(sourceAccount).addMemoIfPresent(memo)
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
                print("  Transaction failed #$i: ")
                println(e.result)
            }
        }
    }
}

private fun Transaction.Builder.addMemoIfPresent(memoText: String?): Transaction.Builder {
    if (memoText != null) {
        this.addMemo(Memo.text(memoText))
    }
    return this
}

interface Network {
    fun accountOf(keyPair: KeyPair): AccountResponse
    fun makeTransaction(transaction: Transaction)
}

class TransactionFailedException(val result: Any, cause: Throwable? = null) : Exception(cause)

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

/**
 * If used, the program must be executed on the host running Stellar Core.
 */
class ProductionNetwork : Network {
    init {
        org.stellar.sdk.Network.usePublicNetwork()
    }

    private val server = Server("https://horizon.stellar.org")

    override fun accountOf(keyPair: KeyPair): AccountResponse = server.accounts().account(keyPair)

    override fun makeTransaction(transaction: Transaction) {
        val blob = URLEncoder.encode(transaction.toEnvelopeXdrBase64(), "UTF-8")
        val stream = try {
            URL("http://localhost:11626/tx?blob=$blob").openStream()
        } catch (e: ConnectException) {
            throw TransactionFailedException("Could not send HTTP command to Stellar Core. Is it running on localhost?", e)
        }
        val result: TransactionResult = parseJson(stream)
        if (result.status != "PENDING") throw TransactionFailedException(result)
    }
}