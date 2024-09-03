package me.taubsie.dungeonhub.application.config

import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartPriority
import me.taubsie.dungeonhub.application.loader.StartupListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

@OnStart(priority = StartPriority.CONFIGURATION_LOADER)
abstract class ConfigFile<T : ChoiceEnum?> : StartupListener {
    private val properties = Properties()

    protected abstract val possibleProperties: Set<T>

    protected abstract val configFile: File

    fun setConfig(property: T, value: String?) {
        properties.setProperty(property!!.readableName, value)

        saveProperties()
    }

    fun getConfig(property: T): String? {
        return properties.getProperty(property!!.readableName)
    }

    fun saveProperties() {
        try {
            Files.newBufferedWriter(configFile.toPath()).use { bufferedWriter ->
                properties.store(bufferedWriter, null)
            }
        } catch (ioException: IOException) {
            logger.error("Error when trying to save properties of file {}.", configFile.name, ioException)
        }
    }

    protected fun reloadConfig() {
        val file = configFile

        if (!file.exists()) {
            if ((file.parentFile != null && !file.parentFile.exists()
                        && !file.parentFile.mkdirs())
            ) {
                return
            }

            try {
                Files.createFile(file.toPath())
            } catch (ioException: IOException) {
                logger.error("Error when trying to create file {}.", file.absolutePath, ioException)
                return
            }

            for (property in possibleProperties) {
                properties.setProperty(property!!.readableName, "")
            }
        } else {
            try {
                Files.newBufferedReader(file.toPath()).use { bufferedReader ->
                    properties.load(bufferedReader)
                }
            } catch (ioException: IOException) {
                logger.error("Error when reading properties from file {}.", file.name, ioException)
            }

            for (property in possibleProperties) {
                if (!properties.containsKey(property!!.readableName)) {
                    properties.setProperty(property.readableName, "")
                }
            }
        }

        try {
            Files.newBufferedWriter(file.toPath()).use { bufferedWriter ->
                properties.store(bufferedWriter, null)
            }
        } catch (ioException: IOException) {
            logger.error("Error when writing properties to file {}.", file.name, ioException)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ConfigFile::class.java)
    }
}