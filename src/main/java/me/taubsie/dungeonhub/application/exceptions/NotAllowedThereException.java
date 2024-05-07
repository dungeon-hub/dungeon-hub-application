package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class NotAllowedThereException extends CommandExecutionException
{
    public NotAllowedThereException() {
        super("You aren't allowed to use this here.");
    }
}