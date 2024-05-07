package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class InvalidSubCommandException extends CommandExecutionException {
    public InvalidSubCommandException() {
        super("Unknown or missing sub-command");
    }
}