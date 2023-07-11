package me.taubsie.dungeonhub.application.exceptions;

public class FailedToLoadException extends RuntimeException {
    public FailedToLoadException(Throwable throwable) {
        super(throwable);
    }

    public FailedToLoadException(String message) {
        super(message);
    }
}