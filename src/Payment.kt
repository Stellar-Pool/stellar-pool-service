package it.menzani.stellarpool

import com.google.gson.Gson
import org.stellar.sdk.*
import org.stellar.sdk.responses.AccountResponse
import org.stellar.sdk.responses.SubmitTransactionResponse
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.util.*

fun main(args: Array<String>) {
    multipleTestnetPayments()
}

// Can be executed locally
private fun multipleTestnetPayments() {
    val paymentExecutor = TestPaymentExecutor()

    val payment = Payment(
            sourceSecretSeed = String(paymentExecutor.sender.secretSeed),
            executor = paymentExecutor)
    for (i in 1..5000) {
        payment.addDestination(
                destinationAccountId = paymentExecutor.receiver.accountId,
                amount = "0.0000001")
    }

    payment.send()
    paymentExecutor.printBalances()
}

// Must be executed on the host running Stellar Core
fun singleMainnetPayment() {
    val paymentExecutor = ProductionPaymentExecutor()

    val payment = Payment(
            sourceSecretSeed = "SCQXGVXBSQHQQ2WYQSODQF6BL6DKKINAFSAF7B5GU72G3USBPJSUIBI4",
            executor = paymentExecutor)
    val destination = KeyPair.fromAccountId("GAJOC4WSOL3VUHYTEQPSOY54LP3XDWBS3AEZZ4DH24NEOHBBMEQKK7I7")
    payment.addDestination(
            destinationAccountId = destination.accountId,
            amount = "0.0000001")

    payment.send()
    println("Destination account balance is:")
    paymentExecutor.printBalance(destination)
}

class Payment(sourceSecretSeed: String, val executor: PaymentExecutor) {
    val sourceKeys: KeyPair = KeyPair.fromSecretSeed(sourceSecretSeed)
    val sourceAccount: AccountResponse = executor.accountOf(sourceKeys)
    val transactionBuilders: Deque<Transaction.Builder> = ArrayDeque<Transaction.Builder>()

    fun addDestination(destinationAccountId: String, amount: String) {
        val destinationKeys = KeyPair.fromAccountId(destinationAccountId)
        val paymentBuilder = PaymentOperation.Builder(destinationKeys, AssetTypeNative(), amount)
        addOperation(paymentBuilder.build())
    }

    private fun addOperation(payment: PaymentOperation) {
        var transactionBuilder = transactionBuilders.peekFirst()
        if (transactionBuilder == null || transactionBuilder.operationsCount == 100) {
            transactionBuilder = Transaction.Builder(sourceAccount)
            val memoText = "stellarpool.net distribution"
            transactionBuilder.addMemo(Memo.text(memoText))
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
            executor.makeTransaction(transaction)
            println("  Executed transaction #${i++}")
        }
    }
}

interface PaymentExecutor {
    fun makeTransaction(transaction: Transaction)
    fun accountOf(keyPair: KeyPair): AccountResponse

    fun printBalance(keyPair: KeyPair) {
        val account: AccountResponse = accountOf(keyPair)
        for (balance in account.balances) {
            println("  Type: ${balance.assetType}, Code: ${balance.assetCode}, Balance: ${balance.balance}")
        }
    }
}

class TestPaymentExecutor : PaymentExecutor {
    init {
        Network.useTestNetwork()
    }

    val server = Server("https://horizon-testnet.stellar.org")

    override fun makeTransaction(transaction: Transaction) {
        val submission: SubmitTransactionResponse = server.submitTransaction(transaction)
        assert(submission.isSuccess, { "Transaction failed." })
    }

    override fun accountOf(keyPair: KeyPair): AccountResponse = server.accounts().account(keyPair)

    // Test utilities
    val sender: KeyPair = KeyPair.fromSecretSeed("SBFJRAEZHMA6GB2OPELLRQVER57V6DLDT6PYJZSJGI4HMZPP7MCJ3QID")
    val receiver: KeyPair = KeyPair.fromSecretSeed("SC4TQUMPJKW5S2UPGLPGSHJDTEEGATW2CS4AHXLJM4USUT2UXVUKVZGJ")

    init {
        assert(sender.accountId == "GDQSOMO3Z2VPQOCKJI2S5BRSVLHO5F5RN6SXKNFLAMGGXINFNTO4YI36")
        assert(receiver.accountId == "GCVQMOB6ASUZUJQ3EGQI7WOOIQ6HI6MVPMQCUO5NJYMXIFW3UUF4LM3C")
    }

    fun printBalances() {
        println("Sender account balance is:")
        printBalance(sender)
        println("Receiver account balance is:")
        printBalance(receiver)
    }
    // ==============
}

class ProductionPaymentExecutor : PaymentExecutor {
    init {
        Network.usePublicNetwork()
    }

    val gson = Gson()
    val server = Server("https://horizon.stellar.org")

    override fun makeTransaction(transaction: Transaction) {
        val blob = URLEncoder.encode(transaction.toEnvelopeXdrBase64(), "UTF-8")
        val stream = URL("http://localhost:11626/tx?blob=$blob").openStream()
        val result = gson.fromJson(InputStreamReader(stream, "UTF-8"), TransactionResult::class.java)
        assert(result.status == "PENDING", { "Transaction failed with result: $result" })
    }

    override fun accountOf(keyPair: KeyPair): AccountResponse = server.accounts().account(keyPair)

    data class TransactionResult(
            val status: String,
            val exception: String
    )
}