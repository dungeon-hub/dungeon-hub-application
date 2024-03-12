package me.taubsie.dungeonhub.application.exceptions;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerNotFoundException extends CommandExecutionException {

    public PlayerNotFoundException(@NotNull String name) {
        super("Player with name \"" + name + "\" not found!");
    }

    public PlayerNotFoundException(@NotNull UUID uuid) {
        super("Player with UUID \"" + uuid + "\" not found!");
    }
}