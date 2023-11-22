package me.taubsie.dungeonhub.application.exceptions;

public class HypixelLinkedToOtherException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "This account is already linked to another discord user.";
    }
}