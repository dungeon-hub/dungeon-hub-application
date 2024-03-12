package me.taubsie.dungeonhub.application.exceptions;

public class NoNameSchemaException extends CommandExecutionException {
    public NoNameSchemaException() {
        super("No role with name-schema to apply found.\n" +
                "Please tell the administrators to set them up correctly.");
    }
}