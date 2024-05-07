package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class MissingPermissionException extends CommandExecutionException
{
    public MissingPermissionException() {
        super("You aren't allowed to use this!");
    }
}