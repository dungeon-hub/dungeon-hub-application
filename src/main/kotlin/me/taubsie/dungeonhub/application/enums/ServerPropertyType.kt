package me.taubsie.dungeonhub.application.enums

/**
 * This represents the type of server property.
 * The default is [.STRING], as the properties are saved as a string.
 * This has effects on the way that values are displayed and on how they are set.
 *
 *
 * Still WIP
 */
enum class ServerPropertyType {
    //TODO finish implementing boolean
    STRING,
    NUMBER,
    BOOLEAN,
    CHANNEL,
    CATEGORY,
    ROLE;

    fun applyPropertyType(value: String): String {
        if (this == CATEGORY) {
            return "<#$value>"
        }

        if (this == CHANNEL) {
            return "<#$value>"
        }

        if (this == ROLE) {
            return "<@&$value>"
        }

        return value
    }

    fun canAccept(value: String?): Boolean {
        //TODO implement
        return true
    }
}