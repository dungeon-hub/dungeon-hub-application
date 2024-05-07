package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class CarryTierNotFoundException extends CommandExecutionException {

    public CarryTierNotFoundException() {
        super("That carry tier was not found.");
    }
}