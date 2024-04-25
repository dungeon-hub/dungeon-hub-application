package me.taubsie.dungeonhub.kord.application.exceptions

import dev.kord.rest.builder.message.EmbedBuilder
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadException

class FailedToLoadEmbedException(@field:Transient val embed: EmbedBuilder) :
    FailedToLoadException("Failed to load the embed data.")