package me.taubsie.dungeonhub.application.exceptions;

public class NotAllowedThereException extends CommandExecutionException
{
    public NotAllowedThereException() {
        super("You aren't allowed to use this here.");
    }
}