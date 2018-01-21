package it.menzani.stellarpool.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.menzani.stellarpool.serialization.pool.Configuration
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

enum class Resources(val resourceName: String) {
    DEFAULT_CONFIG("config.json");

    private val resource: URL = ClassLoader.getSystemResource("it/menzani/stellarpool/$resourceName")

    fun bytes() = resource.readBytes()
}

class ConfigurationFile {
    private val file = Paths.get(Resources.DEFAULT_CONFIG.resourceName)

    init {
        if (Files.notExists(file)) {
            Files.write(file, Resources.DEFAULT_CONFIG.bytes())
            println("Created default configuration file")
        }
    }

    fun open(): Configuration = ObjectMapper().readValue(Files.newBufferedReader(file))
}

class MalformedConfigurationException(fieldName: String, requirement: String) :
        RuntimeException("$fieldName field in ${Resources.DEFAULT_CONFIG.resourceName} $requirement")