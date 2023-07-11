package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.common.exceptions.ProgramException;

public abstract class CommandExecutionException extends ProgramException
{
    @Override
    public abstract String getMessage();
}