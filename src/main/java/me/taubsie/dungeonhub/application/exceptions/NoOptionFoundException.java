package me.taubsie.dungeonhub.application.exceptions;

public class NoOptionFoundException extends CommandExecutionException {
    public NoOptionFoundException() {
        super("Please provide any option when executing this command.");
    }
}