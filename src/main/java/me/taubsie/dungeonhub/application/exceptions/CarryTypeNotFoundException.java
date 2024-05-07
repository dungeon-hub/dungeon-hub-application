package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class CarryTypeNotFoundException extends CommandExecutionException {

    public CarryTypeNotFoundException() {
        super("That carry type was not found.");
    }
}