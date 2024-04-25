package me.taubsie.dungeonhub.application.exceptions;

public class InvalidSubCommandException extends CommandExecutionException {
    public InvalidSubCommandException() {
        super("Unknown or missing sub-command");
    }
}