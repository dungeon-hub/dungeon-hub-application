package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.common.exceptions.ProgramException;

import java.io.Serial;

public abstract class CommandExecutionException extends ProgramException
{
    @Serial
    private static final long serialVersionUID = 6707538877645992492L;

    @Override
    public abstract String getMessage();
}