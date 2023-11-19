package me.taubsie.dungeonhub.application.exceptions;

import java.util.UUID;

public class PlayerNotFoundException extends CommandExecutionException {
    private final UUID uuid;
    private final String name;

    public PlayerNotFoundException(String name) {
        this.uuid = null;
        this.name = name;
    }

    public PlayerNotFoundException(UUID uuid) {
        this.uuid = uuid;
        this.name = null;
    }


    @Override
    public String getMessage() {
        if(uuid != null) {
            return "Player with UUID " + uuid + " not found!";
        }
        return "Player with name " + name + " not found!";
    }
}