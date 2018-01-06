package it.menzani.stellarpool.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.*

interface Logger {
    fun fine(lazyMessage: () -> Any)
    fun info(lazyMessage: () -> Any)
    fun warn(lazyMessage: () -> Any)
    fun fail(lazyMessage: () -> Any)
    fun throwable(throwable: Throwable, lazyMessage: () -> Any)
    fun log(level: Level, lazyMessage: () -> Any)
}

abstract class AbstractLogger : Logger {
    var formatter: Formatter = TimestampFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    var consumers: MutableList<Consumer> = mutableListOf(ConsoleConsumer())

    fun addConsumer(consumer: Consumer): AbstractLogger {
        consumers.add(consumer)
        return this
    }

    override fun fine(lazyMessage: () -> Any) {
        log(Level.FINE, lazyMessage)
    }

    override fun info(lazyMessage: () -> Any) {
        log(Level.INFORMATION, lazyMessage)
    }

    override fun warn(lazyMessage: () -> Any) {
        log(Level.WARNING, lazyMessage)
    }

    override fun fail(lazyMessage: () -> Any) {
        log(Level.FAILURE, lazyMessage)
    }

    override fun throwable(throwable: Throwable, lazyMessage: () -> Any) {
        fail(lazyMessage)
        fail({ throwable.printStackTraceString() })
    }

    override fun log(level: Level, lazyMessage: () -> Any) {
        try {
            doLog(level, lazyMessage)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    abstract fun doLog(level: Level, lazyMessage: () -> Any)
}

private fun Throwable.printStackTraceString(): String {
    val writer = StringWriter()
    writer.use { this.printStackTrace(PrintWriter(writer)) }
    return writer.toString()
}

enum class Level(val marker: String) {
    FINE("FINE"),
    INFORMATION("INFO"),
    WARNING("WARNING"),
    FAILURE("FAILURE")
}

interface Formatter {
    fun format(entry: LogEntry): String
}

class LogEntry(val level: Level, val lazyMessage: () -> Any)

interface Consumer {
    fun consume(entry: String, level: Level)
}

class SynchronousLogger : AbstractLogger() {
    override fun doLog(level: Level, lazyMessage: () -> Any) {
        val entry = formatter.format(LogEntry(level, lazyMessage))
        consumers.stream()
                .forEach({ it.consume(entry, level) })
    }
}

class AsynchronousLogger : AbstractLogger() {
    private val executor: ExecutorService = newSingleThreadDaemonExecutor()
    private val queue: BlockingQueue<LogEntry> = LinkedBlockingQueue<LogEntry>()

    init {
        executor.execute(Task())
    }

    override fun doLog(level: Level, lazyMessage: () -> Any) {
        queue.add(LogEntry(level, lazyMessage))
    }

    private inner class Task : Runnable {
        override fun run() {
            while (true) {
                val entry = queue.take()
                val formattedEntry = formatter.format(entry)
                consumers.parallelStream()
                        .forEach({ it.consume(formattedEntry, entry.level) })
            }
        }
    }
}

private fun newSingleThreadDaemonExecutor(): ExecutorService = Executors.newSingleThreadExecutor(object : ThreadFactory {
    private val delegate = Executors.defaultThreadFactory()

    override fun newThread(r: Runnable): Thread {
        val thread = delegate.newThread(r)
        thread.isDaemon = true
        return thread
    }
})

class TimestampFormatter(private val formatter: DateTimeFormatter) : Formatter {
    override fun format(entry: LogEntry): String {
        val dateTime = formatter.format(LocalDateTime.now())
        return "[$dateTime ${entry.level.marker}] ${entry.lazyMessage()}"
    }
}

class ConsoleConsumer : Consumer {
    override fun consume(entry: String, level: Level) {
        when (level) {
            Level.FINE, Level.INFORMATION, Level.WARNING -> System.out.println(entry)
            Level.FAILURE -> System.err.println(entry)
        }
    }
}

class FileConsumer(pathToFile: Path) : Consumer {
    private val writer = Files.newOutputStream(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND).writer()
    private val lineSeparator = System.lineSeparator()

    override fun consume(entry: String, level: Level) {
        writer.write(entry)
        writer.write(lineSeparator)
        writer.flush()
    }
}