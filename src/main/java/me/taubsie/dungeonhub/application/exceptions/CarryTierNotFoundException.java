package me.taubsie.dungeonhub.application.exceptions;

public class CarryTierNotFoundException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "That carry tier was not found.";
    }
}