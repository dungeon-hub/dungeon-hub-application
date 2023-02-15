package me.taubsie.carrylogs.application.exceptions;

public abstract class CommandExecutionException extends IllegalArgumentException
{
    public abstract String getMessage();
}