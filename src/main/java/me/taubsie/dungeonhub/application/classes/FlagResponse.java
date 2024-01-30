package me.taubsie.dungeonhub.application.classes;

import org.jetbrains.annotations.Nullable;

public record FlagResponse(String name, @Nullable FlagDetail uuid, @Nullable FlagDetail discord) {
}