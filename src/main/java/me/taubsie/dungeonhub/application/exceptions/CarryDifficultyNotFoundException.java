package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class CarryDifficultyNotFoundException extends CommandExecutionException {

    public CarryDifficultyNotFoundException() {
        super("That carry difficulty was not found.");
    }
}