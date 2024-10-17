package me.taubsie.dungeonhub.application.misc

import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException

@JvmRecord
data class HelpDisplay(val description: String, val embedColor: EmbedColor, val fields: Map<String, String>) {
    companion object {
        fun fromDescription(description: String): HelpDisplay {
            return HelpDisplay(description, EmbedColor.Default, HashMap())
        }

        fun fromException(commandExecutionException: CommandExecutionException): HelpDisplay {
            return HelpDisplay(commandExecutionException.message!!, EmbedColor.Negative, HashMap())
        }
    }
}