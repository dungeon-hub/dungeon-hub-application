package me.taubsie.dungeonhub.application.exceptions;

public class MustBeServerException extends CommandExecutionException
{
    public MustBeServerException() {
        super("Please use this on a server!");
    }
}