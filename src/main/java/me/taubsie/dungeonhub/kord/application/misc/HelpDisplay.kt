package me.taubsie.dungeonhub.kord.application.misc

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor

@JvmRecord
data class HelpDisplay(val description: String, val embedColor: EmbedColor, val fields: Map<String, String>) {
    companion object {
        fun fromDescription(description: String): HelpDisplay {
            return HelpDisplay(description, EmbedColor.DEFAULT, HashMap())
        }

        fun fromException(commandExecutionException: CommandExecutionException): HelpDisplay {
            return HelpDisplay(commandExecutionException.message!!, EmbedColor.NEGATIVE, HashMap())
        }
    }
}