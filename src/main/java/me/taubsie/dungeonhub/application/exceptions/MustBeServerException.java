package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class MustBeServerException extends CommandExecutionException
{
    public MustBeServerException() {
        super("Please use this on a server!");
    }
}