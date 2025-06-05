package net.dungeonhub.application.misc

import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException

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