package me.taubsie.dungeonhub.application.exceptions;

public class NoNameSchemaException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "No role with name-schema to apply found.\n" +
                "Please tell the administrators to set them up correctly.";
    }
}