package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class NoNameSchemaException extends CommandExecutionException {
    public NoNameSchemaException() {
        super("No role with name-schema to apply found.\n" +
                "Please tell the administrators to set them up correctly.");
    }
}