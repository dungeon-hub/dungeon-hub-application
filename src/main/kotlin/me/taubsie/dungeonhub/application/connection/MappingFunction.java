package me.taubsie.dungeonhub.application.connection;

import java.io.IOException;

@FunctionalInterface
public interface MappingFunction<T, R> {
    R apply(T t) throws IOException;
}