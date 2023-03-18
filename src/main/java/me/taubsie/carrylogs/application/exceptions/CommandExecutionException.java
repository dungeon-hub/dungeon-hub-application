package me.taubsie.carrylogs.application.exceptions;

import me.taubsie.dungeonhub.common.exceptions.ProgramException;

public abstract class CommandExecutionException extends ProgramException
{
    @Override
    public abstract String getMessage();
}