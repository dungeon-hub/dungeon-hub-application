package me.taubsie.dungeonhub.application.exceptions;

import lombok.NoArgsConstructor;
import me.taubsie.dungeonhub.common.exceptions.ProgramException;

import java.io.Serial;

@NoArgsConstructor
public class CommandExecutionException extends ProgramException
{
    @Serial
    private static final long serialVersionUID = 6707538877645992492L;


    public CommandExecutionException(String s) {
        super(s);
    }

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommandExecutionException(Throwable cause) {
        super(cause);
    }
}