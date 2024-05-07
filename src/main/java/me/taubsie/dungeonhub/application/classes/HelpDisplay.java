package me.taubsie.dungeonhub.application.classes;

import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public record HelpDisplay(@NotNull String description, EmbedColor embedColor, @NotNull Map<String, String> fields) {
    public static HelpDisplay fromDescription(@NotNull String description) {
        return new HelpDisplay(description, EmbedColor.DEFAULT, new HashMap<>());
    }

    public static HelpDisplay fromException(CommandExecutionException commandExecutionException) {
        return new HelpDisplay(commandExecutionException.getMessage(), EmbedColor.NEGATIVE, new HashMap<>());
    }
}