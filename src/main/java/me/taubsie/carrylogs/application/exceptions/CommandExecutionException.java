package me.taubsie.carrylogs.application.exceptions;

public abstract class CommandExecutionException extends IllegalArgumentException
{
    @Override
    public abstract String getMessage();
}