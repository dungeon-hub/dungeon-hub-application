package me.taubsie.dungeonhub.application.exceptions;

public class InvalidSubCommandException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "Unknown or missing sub-command";
    }
}