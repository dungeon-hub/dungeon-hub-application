package me.taubsie.dungeonhub.application.exceptions;

import org.javacord.api.entity.message.embed.EmbedBuilder;

public class FailedToLoadEmbedException extends FailedToLoadException {
    private final EmbedBuilder embedBuilder;

    public FailedToLoadEmbedException(EmbedBuilder embedBuilder) {
        super("Failed to load the embed data.");
        this.embedBuilder = embedBuilder;
    }

    public EmbedBuilder getEmbed() {
        return embedBuilder;
    }
}