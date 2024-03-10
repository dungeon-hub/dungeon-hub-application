package me.taubsie.dungeonhub.application.exceptions;

public class CarryTypeNotFoundException extends CommandExecutionException {

    public CarryTypeNotFoundException() {
        super("That carry type was not found.");
    }
}