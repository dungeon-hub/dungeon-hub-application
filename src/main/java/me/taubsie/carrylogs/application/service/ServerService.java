package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.classes.ServerData;
import me.taubsie.carrylogs.application.classes.ServerProperty;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.CarryLogService;
import me.taubsie.dungeonhub.common.OnStart;
import me.taubsie.dungeonhub.common.ProgramOrigin;
import me.taubsie.dungeonhub.common.StartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Time;
import java.util.*;

@OnStart
public class ServerService implements StartupListener {
    private static final Logger logger = LoggerFactory.getLogger(ServerService.class);
    private static ServerService instance;
    private final Set<ServerData> serverData = new HashSet<>();
    private Timer timer;

    public static ServerService getInstance() {
        if(instance == null) {
            instance = new ServerService();
        }

        return instance;
    }

    private ServerService() {
        try {
            Files.createDirectory(Path.of(getServerFolder()));
        } catch(FileAlreadyExistsException ignored) {
            //Ignored since I just want to be sure that the folder always exists.
        } catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void loadServers() {
        serverData.clear();

        BotStarter.getInstance()
                .getBot()
                .getServers()
                .forEach(server -> loadServerData(server.getId()));
    }

    private void resetTimer() {
        if(timer != null) {
            timer.cancel();
        }

        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                logger.debug("Server configs reloaded!");
                loadServers();
            }
        }, new Time(System.currentTimeMillis() + 1000 * 60 * 15), 1000L * 60 * 15);
    }

    public String getServerFolder() {
        return CarryLogService.getInstance().getMainFolder() + File.separator + "servers";
    }

    public void loadServerData(long id) {
        serverData.add(new ServerData(id));
    }

    public void unloadServerData(long id) {
        serverData.removeIf(serverData1 -> serverData1.getId() == id);
    }

    public Optional<ServerData> getServerData(long id) {
        return serverData.stream()
                .filter(data -> data.getId() == id)
                .findAny();
    }

    public String getServerProperty(long id, ServerProperty serverProperty) {
        return getServerData(id)
                .map(data -> data.getConfig(serverProperty))
                .filter(s -> !s.isBlank())
                .orElse(serverProperty.getDefaultValue());
    }

    public Optional<String> getActualServerProperty(long id, ServerProperty serverProperty) {
        return getServerData(id)
                .map(data -> data.getConfig(serverProperty))
                .filter(s -> !s.isBlank());
    }

    public boolean canUse(long id, @Nullable ServerProperty serverProperty) {
        //TODO finish implementation (if needed here)
        if(serverProperty == null) {
            return false;
        }

        return getServerData(id)
                .map(serverData1 -> serverData1.isEnabled(serverProperty))
                .orElse(false);
    }

    @Override
    public void onStart(ProgramOrigin programOrigin) {
        loadServers();

        resetTimer();
    }
}