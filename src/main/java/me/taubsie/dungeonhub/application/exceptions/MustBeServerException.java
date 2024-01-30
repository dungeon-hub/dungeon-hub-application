package me.taubsie.dungeonhub.application.exceptions;

public class MustBeServerException extends CommandExecutionException
{
    @Override
    public String getMessage()
    {
        return "Please use this on a server!";
    }
}