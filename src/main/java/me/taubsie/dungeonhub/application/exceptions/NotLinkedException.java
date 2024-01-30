package me.taubsie.dungeonhub.application.exceptions;

public class NotLinkedException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "You aren't verified yet! Try using `/link`.";
    }
}