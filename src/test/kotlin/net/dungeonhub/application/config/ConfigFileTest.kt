package net.dungeonhub.application.config

import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.i18n.toKey
import dev.kordex.i18n.Key
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigFileTest {
    private enum class TestProperty(override val readableName: Key) : ChoiceEnum {
        TOKEN("test.token".toKey()),
        URL("test.url".toKey()),
        NEW_PROPERTY("test.new-property".toKey())
    }

    /**
     * Simulates a full disk: once [diskFull] is set, every write puts only a part of the content on the disk
     * and then fails, just like a real write would when running out of space.
     */
    private class TestConfigFile(
        override val configFile: File,
        override val possibleProperties: Set<TestProperty> = TestProperty.entries.toSet()
    ) : ConfigFile<TestProperty>() {
        var diskFull = false
        var writes = 0

        override fun writeFully(path: Path, content: ByteArray) {
            writes++

            if (diskFull) {
                Files.write(path, content.copyOf(content.size / 3))
                throw IOException("No space left on device")
            }

            super.writeFully(path, content)
        }

        suspend fun reload() = reloadConfig()
    }

    private lateinit var directory: Path
    private lateinit var file: File

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory("config-file-test")
        file = directory.resolve("config.properties").toFile()
    }

    @AfterTest
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    private fun writeConfig(vararg values: Pair<TestProperty, String>) {
        val properties = Properties()
        values.forEach { (property, value) -> properties.setProperty(property.readableName.key, value) }
        file.bufferedWriter().use { properties.store(it, null) }
    }

    private fun readConfig(): Properties {
        return Properties().also { properties -> file.bufferedReader().use { properties.load(it) } }
    }

    private fun assertNoTemporaryFilesLeft() {
        assertEquals(listOf(file.name), directory.toFile().list()!!.toList())
    }

    @Test
    fun `config file survives a full disk while saving`() = runBlocking {
        writeConfig(TestProperty.TOKEN to "secret-token", TestProperty.URL to "https://dungeon-hub.net")
        val originalContent = file.readBytes()

        val config = TestConfigFile(file)
        config.reload()

        config.diskFull = true
        config.setConfig(TestProperty.URL, "https://example.com")

        assertTrue(originalContent.contentEquals(file.readBytes()), "The config file must not be modified")
        assertNoTemporaryFilesLeft()

        // The bot keeps working with the new value in memory.
        assertEquals("https://example.com", config.getConfig(TestProperty.URL))
        assertEquals("secret-token", config.getConfig(TestProperty.TOKEN))
    }

    @Test
    fun `config file survives a full disk while reloading`() = runBlocking {
        // A missing property forces the reload to write the file.
        writeConfig(TestProperty.TOKEN to "secret-token", TestProperty.URL to "https://dungeon-hub.net")
        val originalContent = file.readBytes()

        val config = TestConfigFile(file)
        config.diskFull = true
        config.reload()

        assertTrue(originalContent.contentEquals(file.readBytes()), "The config file must not be modified")
        assertNoTemporaryFilesLeft()
        assertEquals("secret-token", config.getConfig(TestProperty.TOKEN))
    }

    @Test
    fun `config survives a restart after the disk was full`() = runBlocking {
        writeConfig(TestProperty.TOKEN to "secret-token", TestProperty.URL to "https://dungeon-hub.net")

        val config = TestConfigFile(file)
        config.reload()
        config.diskFull = true
        config.setConfig(TestProperty.URL, "https://example.com")
        config.reload()

        val restartedConfig = TestConfigFile(file)
        restartedConfig.reload()

        assertEquals("secret-token", restartedConfig.getConfig(TestProperty.TOKEN))
        assertEquals("https://dungeon-hub.net", restartedConfig.getConfig(TestProperty.URL))
    }

    @Test
    fun `changes that failed to save are written once space is available again`() = runBlocking {
        writeConfig(
            TestProperty.TOKEN to "secret-token",
            TestProperty.URL to "https://dungeon-hub.net",
            TestProperty.NEW_PROPERTY to ""
        )

        val config = TestConfigFile(file)
        config.reload()
        config.diskFull = true
        config.setConfig(TestProperty.URL, "https://example.com")

        config.diskFull = false
        config.reload()

        assertEquals("https://example.com", readConfig().getProperty(TestProperty.URL.readableName.key))
        assertEquals("secret-token", readConfig().getProperty(TestProperty.TOKEN.readableName.key))
        assertNoTemporaryFilesLeft()
    }

    @Test
    fun `reload does not write the file if nothing changed`() = runBlocking {
        writeConfig(
            TestProperty.TOKEN to "secret-token",
            TestProperty.URL to "https://dungeon-hub.net",
            TestProperty.NEW_PROPERTY to ""
        )

        val config = TestConfigFile(file)
        config.reload()
        config.reload()

        assertEquals(0, config.writes)
    }

    @Test
    fun `reload adds missing properties without losing existing values`() = runBlocking {
        writeConfig(TestProperty.TOKEN to "secret-token")

        TestConfigFile(file).reload()

        val properties = readConfig()
        assertEquals("secret-token", properties.getProperty(TestProperty.TOKEN.readableName.key))
        assertEquals("", properties.getProperty(TestProperty.URL.readableName.key))
        assertEquals("", properties.getProperty(TestProperty.NEW_PROPERTY.readableName.key))
    }

    @Test
    fun `missing config file is created`() = runBlocking {
        val nestedFile = directory.resolve("nested").resolve("config.properties").toFile()

        TestConfigFile(nestedFile).reload()

        assertTrue(nestedFile.exists())
        val properties = Properties().also { properties -> nestedFile.bufferedReader().use { properties.load(it) } }
        assertEquals(TestProperty.entries.map { it.readableName.key }.toSet(), properties.stringPropertyNames())
    }

    @Test
    fun `values in memory are restored if the file got lost`() = runBlocking {
        writeConfig(TestProperty.TOKEN to "secret-token")

        val config = TestConfigFile(file)
        config.reload()

        file.writeText("")
        config.reload()

        assertEquals("secret-token", config.getConfig(TestProperty.TOKEN))
        assertEquals("secret-token", readConfig().getProperty(TestProperty.TOKEN.readableName.key))

        assertTrue(file.delete())
        config.reload()

        assertEquals("secret-token", readConfig().getProperty(TestProperty.TOKEN.readableName.key))
    }

    @Test
    fun `saving does not leave a temporary file behind`() = runBlocking {
        val config = TestConfigFile(file)
        config.reload()
        config.setConfig(TestProperty.TOKEN, "secret-token")

        assertEquals("secret-token", readConfig().getProperty(TestProperty.TOKEN.readableName.key))
        assertNoTemporaryFilesLeft()
        assertFalse(File(file.path + ".tmp").exists())
    }
}
