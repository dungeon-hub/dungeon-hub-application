package me.taubsie.dungeonhub.application.exceptions;

public class UnknownCommandException extends CommandExecutionException
{
    @Override
    public String getMessage()
    {
        return "Unknown command.";
    }
}