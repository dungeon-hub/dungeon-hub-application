package me.taubsie.dungeonhub.kord.application.exceptions

import dev.kord.rest.builder.message.EmbedBuilder

class FailedToLoadEmbedException(@field:Transient val embed: EmbedBuilder) :
    FailedToLoadException("Failed to load the embed data.")