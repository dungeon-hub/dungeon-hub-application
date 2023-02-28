package me.taubsie.carrylogs.application.exceptions;

public class FailedToLoadException extends RuntimeException {
    public FailedToLoadException(Throwable throwable) {
        super(throwable);
    }
}