package me.taubsie.dungeonhub.application.exceptions;

public class CarryDifficultyNotFoundException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "That carry difficulty was not found.";
    }
}