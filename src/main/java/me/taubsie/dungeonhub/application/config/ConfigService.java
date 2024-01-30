package me.taubsie.dungeonhub.application.config;

import me.taubsie.dungeonhub.application.loader.OnStart;
import me.taubsie.dungeonhub.common.DungeonHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Time;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

@OnStart
public class ConfigService extends ConfigFile<ConfigProperty> {
    private static ConfigService instance;
    private final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private Timer timer;

    public static ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }

        return instance;
    }

    public Set<ConfigProperty> getPossibleProperties() {
        return ConfigProperty.getProperties();
    }

    private void resetTimer() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                logger.debug("Config reloaded!");
                reloadConfig();
            }
        }, new Time(System.currentTimeMillis() + 10000), 1000L * 60 * 5);
    }

    public String getConfigFolder() {
        return DungeonHubService.getInstance().getMainFolder() + File.separator + "config";
    }

    @Override
    public File getConfigFile() {
        return new File(getConfigFolder() + File.separator + "application_config.properties");
    }

    @Override
    public void preStart() {
        reloadConfig();
    }

    @Override
    public void onStart() {
        resetTimer();
    }
}