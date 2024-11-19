package me.taubsie.dungeonhub.application.misc

data class FlagResponse(val name: String, val uuidGiven: Boolean, val uuid: FlagDetail?, val discordGiven: Boolean, val discord: FlagDetail?)