package me.taubsie.dungeonhub.application.classes;

import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public record HelpDisplay(@NotNull String description, URL attachmentUrl, EmbedColor embedColor, @NotNull Map<String, String> fields) {
    public static HelpDisplay fromDescription(String description) {
        return fromDescriptionAndAttachment(description, null);
    }

    public static HelpDisplay fromDescriptionAndAttachment(@NotNull String description, URL attachmentUrl) {
        return new HelpDisplay(description, attachmentUrl, EmbedColor.DEFAULT, new HashMap<>());
    }

    public static HelpDisplay fromException(CommandExecutionException commandExecutionException) {
        return new HelpDisplay(commandExecutionException.getMessage(), null, EmbedColor.NEGATIVE, new HashMap<>());
    }
}