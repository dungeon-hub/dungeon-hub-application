package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InvalidOptionException extends CommandExecutionException
{
    public InvalidOptionException(@NotNull String name)
    {
        this(name, null);
    }

    public InvalidOptionException(@NotNull String name, @Nullable String additionalMessage)
    {
        super(getMessage(name, additionalMessage));
    }

    private static @NotNull String getMessage(@NotNull String name, @Nullable String additionalMessage)
    {
        if (additionalMessage == null)
        {
            return String.format("The option \"%s\" you entered is invalid.", name);
        }
        else
        {
            return String.format("The option \"%s\" you entered is invalid:%n%s", name, additionalMessage);
        }
    }
}