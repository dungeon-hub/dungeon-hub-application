package net.dungeonhub.application.config

import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.DhScheduler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*

@OnStart(priority = StartPriority.CONFIGURATION_LOADER)
abstract class ConfigFile<T : ChoiceEnum> : StartupListener {
    private val properties = Properties()

    /**
     * Set when the in-memory properties could not be written to disk, e.g. because the disk is full.
     * As long as this is set, the in-memory properties are newer than the file and must not be overwritten by it.
     */
    @Volatile
    private var hasUnsavedChanges = false

    /**
     * Saves of the same file share the temporary file, so they must not run at the same time.
     */
    private val writeMutex = Mutex()

    protected abstract val possibleProperties: Set<T>

    protected abstract val configFile: File

    suspend fun setConfig(property: T, value: String?) {
        if(value == null) {
            properties.remove(property.readableName.key)
        } else {
            properties.setProperty(property.readableName.key, value)
        }

        saveProperties()
    }

    fun getConfig(property: T): String? {
        return properties.getProperty(property.readableName.key)
    }

    suspend fun saveProperties() {
        try {
            writeMutex.withLock {
                withContext(scheduler.coroutineContext) {
                    writeProperties(configFile, properties)
                }
            }
            hasUnsavedChanges = false
        } catch (ioException: IOException) {
            hasUnsavedChanges = true
            logger.error("Error when trying to save properties of file {}.", configFile.name, ioException)
        }
    }

    protected suspend fun reloadConfig() {
        val file = configFile

        if (file.parentFile != null && !file.parentFile.exists() && !file.parentFile.mkdirs()) {
            logger.error("Could not create directory {}.", file.parentFile.absolutePath)
            return
        }

        val fileProperties = Properties()

        if (file.exists()) {
            try {
                withContext(scheduler.coroutineContext) {
                    Files.newBufferedReader(file.toPath()).use { bufferedReader ->
                        fileProperties.load(bufferedReader)
                    }
                }
            } catch (ioException: IOException) {
                // Writing back now could replace the file with incomplete data, so keep everything as it is.
                logger.error("Error when reading properties from file {}.", file.name, ioException)
                return
            }
        }

        // Changes that could not be saved yet are newer than the file, so the file must not overwrite them.
        // Otherwise the file wins, but values only known in memory (e.g. because the file got lost) are kept.
        if (!hasUnsavedChanges) {
            for ((key, value) in fileProperties) {
                properties[key] = value
            }
        }

        for (property in possibleProperties) {
            if (!properties.containsKey(property.readableName.key)) {
                properties.setProperty(property.readableName.key, "")
            }
        }

        // Only write when the file is actually missing something, every write is a chance to fail.
        if (file.exists() && fileProperties == properties) {
            return
        }

        try {
            writeMutex.withLock {
                withContext(scheduler.coroutineContext) {
                    writeProperties(file, properties)
                }
            }
            hasUnsavedChanges = false
        } catch (ioException: IOException) {
            hasUnsavedChanges = true
            logger.error("Error when writing properties to file {}.", file.name, ioException)
        }
    }

    /**
     * Writes the properties to a temporary file first and only replaces the actual file once that succeeded.
     * This way the existing file stays intact if writing fails half-way, e.g. because the disk is full.
     */
    private fun writeProperties(file: File, properties: Properties) {
        val content = StringWriter().also { properties.store(it, null) }.toString().toByteArray(Charsets.UTF_8)

        val target = file.toPath()
        val temporaryFile = target.resolveSibling(target.fileName.toString() + ".tmp")

        try {
            writeFully(temporaryFile, content)

            try {
                Files.move(
                    temporaryFile, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (atomicMoveNotSupported: AtomicMoveNotSupportedException) {
                logger.debug("Atomic move not supported for {}, falling back to a regular move.", file.name, atomicMoveNotSupported)
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (ioException: IOException) {
            try {
                Files.deleteIfExists(temporaryFile)
            } catch (deleteException: IOException) {
                ioException.addSuppressed(deleteException)
            }

            throw ioException
        }
    }

    /**
     * Writes the content to the given file and makes sure it actually reached the disk.
     */
    protected open fun writeFully(path: Path, content: ByteArray) {
        FileChannel.open(
            path,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
        ).use { channel ->
            val buffer = ByteBuffer.wrap(content)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ConfigFile::class.java)
        internal val scheduler = DhScheduler()
    }
}
