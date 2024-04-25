package me.taubsie.dungeonhub.application.exceptions;

public class ChannelNotFoundException extends CommandExecutionException {
    public ChannelNotFoundException() {
        super("The given channel wasn't found.");
    }
}