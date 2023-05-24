package me.taubsie.carrylogs.application.exceptions;

public class ChannelNotFoundException extends CommandExecutionException {
    @Override
    public String getMessage() {
        return "The given channel wasn't found.";
    }
}