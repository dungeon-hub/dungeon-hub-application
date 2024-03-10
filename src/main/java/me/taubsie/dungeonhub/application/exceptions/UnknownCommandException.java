package me.taubsie.dungeonhub.application.exceptions;

public class UnknownCommandException extends CommandExecutionException
{
    public UnknownCommandException() {
        super("Unknown command.");
    }
}