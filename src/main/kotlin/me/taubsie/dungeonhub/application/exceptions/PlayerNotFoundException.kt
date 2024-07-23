package me.taubsie.dungeonhub.application.exceptions

import java.util.*

class PlayerNotFoundException : CommandExecutionException {
    constructor(name: String) : super("Player with name \"$name\" not found!\n\nNOTE: If you think this is a mistake, try again in a few minutes.")

    constructor(uuid: UUID) : super("Player with UUID \"$uuid\" not found!")
}