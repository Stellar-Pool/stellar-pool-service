package it.menzani.stellarpool.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream
import java.io.Reader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

val mapper = ObjectMapper()

inline fun <reified T> parseJson(reader: Reader): T = mapper.readValue(reader, T::class.java)
inline fun <reified T> parseJson(stream: InputStream): T = mapper.readValue(stream, T::class.java)
fun createJson(value: Any): String = mapper.writeValueAsString(value)

enum class Resources(val resourceName: String) {
    DEFAULT_CONFIG("config.json");

    private val resource: URL = ClassLoader.getSystemResource("it/menzani/stellarpool/$resourceName")

    fun bytes() = resource.readBytes()
}

class ConfigurationFile {
    private val file: Path = Paths.get(Resources.DEFAULT_CONFIG.resourceName)

    init {
        if (Files.notExists(file)) {
            Files.write(file, Resources.DEFAULT_CONFIG.bytes())
            println("Created default configuration file")
        }
    }

    fun open(): Configuration = parseJson(Files.newBufferedReader(file))
}

class MalformedConfigurationException(fieldName: String, requirement: String) :
        RuntimeException("$fieldName field in ${Resources.DEFAULT_CONFIG.resourceName} $requirement")