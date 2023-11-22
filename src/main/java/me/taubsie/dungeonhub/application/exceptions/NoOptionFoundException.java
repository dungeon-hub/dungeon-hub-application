package me.taubsie.dungeonhub.application.exceptions;

public class NoOptionFoundException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "Please provide any option when executing this command.";
    }
}