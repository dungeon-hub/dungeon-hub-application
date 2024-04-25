package me.taubsie.dungeonhub.application.exceptions;

public class MissingPermissionException extends CommandExecutionException
{
    public MissingPermissionException() {
        super("You aren't allowed to use this!");
    }
}