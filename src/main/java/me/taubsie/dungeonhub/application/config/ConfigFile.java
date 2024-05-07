package me.taubsie.dungeonhub.application.config;

import me.taubsie.dungeonhub.common.Nameable;
import me.taubsie.dungeonhub.kord.application.loader.OnStart;
import me.taubsie.dungeonhub.kord.application.loader.StartPriority;
import me.taubsie.dungeonhub.kord.application.loader.StartupListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Set;

@OnStart(priority = StartPriority.CONFIGURATION_LOADER)
public abstract class ConfigFile<T extends Nameable> implements StartupListener {
    private static final Logger logger = LoggerFactory.getLogger(ConfigFile.class);

    private final Properties properties = new Properties();

    protected abstract Set<T> getPossibleProperties();

    protected abstract File getConfigFile();

    public void setConfig(T property, String value) {
        properties.setProperty(property.getName(), value);

        saveProperties();
    }

    @Nullable
    public String getConfig(T property) {
        return properties.getProperty(property.getName());
    }

    public void saveProperties() {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(getConfigFile().toPath())) {
            properties.store(bufferedWriter, null);
        }
        catch (IOException ioException) {
            logger.error("Error when trying to save properties of file {}.", getConfigFile().getName(), ioException);
        }
    }

    protected void reloadConfig() {
        File file = getConfigFile();

        if (!file.exists()) {
            if (file.getParentFile() != null
                    && !file.getParentFile().exists()
                    && !file.getParentFile().mkdirs()) {
                return;
            }

            try {
                Files.createFile(file.toPath());
            }
            catch (IOException ioException) {
                logger.error("Error when trying to create file {}.", file.getAbsolutePath(), ioException);
                return;
            }

            for(T property : getPossibleProperties()) {
                properties.setProperty(property.getName(), "");
            }
        } else {
            try (BufferedReader bufferedReader = Files.newBufferedReader(file.toPath())) {
                properties.load(bufferedReader);
            }
            catch (IOException ioException) {
                logger.error("Error when reading properties from file {}.", file.getName(), ioException);
            }

            for(T property : getPossibleProperties()) {
                if (!properties.containsKey(property.getName())) {
                    properties.setProperty(property.getName(), "");
                }
            }
        }

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(file.toPath())) {
            properties.store(bufferedWriter, null);
        }
        catch (IOException ioException) {
            logger.error("Error when writing properties to file {}.", file.getName(), ioException);
        }
    }
}