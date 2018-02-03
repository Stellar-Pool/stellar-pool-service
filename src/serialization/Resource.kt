package it.menzani.stellarpool.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.menzani.stellarpool.serialization.pool.Configuration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class Resources(private val resourceName: String) {
    DEFAULT_CONFIG("config.json");

    private val resource = ClassLoader.getSystemResource("it/menzani/stellarpool/$resourceName")

    fun bytes() = resource.readBytes()

    fun toPath(): Path = Paths.get(resourceName)

    inline fun <reified T : Any> json(): T = mapper.readValue(Files.newBufferedReader(toPath()))

    override fun toString() = resourceName

    companion object {
        val mapper = ObjectMapper()
    }
}

class ConfigurationFile {
    init {
        val file = Resources.DEFAULT_CONFIG.toPath()
        if (Files.notExists(file)) {
            Files.write(file, Resources.DEFAULT_CONFIG.bytes())
            println("Created default configuration file")
        }
    }

    fun open(): Configuration = Resources.DEFAULT_CONFIG.json()
}

class MalformedConfigurationException(fieldName: String, requirement: String) :
        RuntimeException("$fieldName field in ${Resources.DEFAULT_CONFIG} $requirement")