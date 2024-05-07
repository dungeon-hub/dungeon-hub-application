package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class NoOptionFoundException extends CommandExecutionException {
    public NoOptionFoundException() {
        super("Please provide any option when executing this command.");
    }
}