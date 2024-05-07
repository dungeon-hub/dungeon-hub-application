package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class NotLinkedException extends CommandExecutionException {
    public NotLinkedException() {
        super("You aren't verified yet! Try using `/link`.");
    }
}