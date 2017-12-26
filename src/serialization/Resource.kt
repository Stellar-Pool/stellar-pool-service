package it.menzani.stellarpool.serialization

import com.google.gson.Gson
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Paths

val gson = Gson()

inline fun <reified T> parseJson(reader: Reader): T = gson.fromJson(reader, T::class.java)
inline fun <reified T> parseJson(stream: InputStream, charsetName: String = "UTF-8"): T = parseJson(InputStreamReader(stream, charsetName))

enum class Resources(val resourceName: String) {
    DEFAULT_CONFIG("config.json");

    val resource = ClassLoader.getSystemResource("it/menzani/stellarpool/$resourceName")

    fun bytes() = resource.readBytes()
}

class ConfigurationFile {
    val file = Paths.get(Resources.DEFAULT_CONFIG.resourceName)

    init {
        if (Files.notExists(file)) {
            Files.write(file, Resources.DEFAULT_CONFIG.bytes())
            println("Created default configuration file")
        }
    }

    fun open() = parseJson<Configuration>(Files.newBufferedReader(file))
}