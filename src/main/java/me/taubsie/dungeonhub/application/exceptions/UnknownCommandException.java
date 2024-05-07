package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class UnknownCommandException extends CommandExecutionException
{
    public UnknownCommandException() {
        super("Unknown command.");
    }
}