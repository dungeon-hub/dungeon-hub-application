package me.taubsie.carrylogs.application.exceptions;

public class MissingPermissionException extends CommandExecutionException
{
    @Override
    public String getMessage()
    {
        return "You aren't allowed to use this!";
    }
}