package me.taubsie.dungeonhub.application.exceptions;

public class CarryTypeNotFoundException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "That carry type was not found.";
    }
}