package me.taubsie.dungeonhub.application.exceptions;

public class CarryTierNotFoundException extends CommandExecutionException {

    public CarryTierNotFoundException() {
        super("That carry tier was not found.");
    }
}