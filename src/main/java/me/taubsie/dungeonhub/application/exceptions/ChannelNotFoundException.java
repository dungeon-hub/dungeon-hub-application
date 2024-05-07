package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class ChannelNotFoundException extends CommandExecutionException {
    public ChannelNotFoundException() {
        super("The given channel wasn't found.");
    }
}