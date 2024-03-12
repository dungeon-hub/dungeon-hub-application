package me.taubsie.dungeonhub.application.exceptions;

public class CarryDifficultyNotFoundException extends CommandExecutionException {

    public CarryDifficultyNotFoundException() {
        super("That carry difficulty was not found.");
    }
}