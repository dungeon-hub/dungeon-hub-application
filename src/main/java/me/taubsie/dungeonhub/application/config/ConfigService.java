package me.taubsie.dungeonhub.application.config;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.kord.application.loader.OnStart;
import me.taubsie.dungeonhub.kord.application.loader.StartPriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Time;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

@OnStart(priority = StartPriority.CONFIGURATION_LOADER)
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

    @Nullable
    @Override
    public Object preStart(@NotNull Continuation<? super Unit> $completion) {
        reloadConfig();
        return null;
    }

    @Nullable
    @Override
    public Object onStart(@NotNull Continuation<? super Unit> $completion) {
        resetTimer();
        return null;
    }

    @Nullable
    @Override
    public Object postStart(@NotNull Continuation<? super Unit> $completion) {
        return null;
    }
}