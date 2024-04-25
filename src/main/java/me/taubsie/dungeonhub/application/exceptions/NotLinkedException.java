package me.taubsie.dungeonhub.application.exceptions;

public class NotLinkedException extends CommandExecutionException {
    public NotLinkedException() {
        super("You aren't verified yet! Try using `/link`.");
    }
}