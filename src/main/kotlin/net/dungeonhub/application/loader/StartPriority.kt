package net.dungeonhub.application.loader

enum class StartPriority(val priority: Int) {
    CLASS_LOADER(-5),
    CONFIGURATION_LOADER(-1),
    PRE_BOT(0),
    DISCORD_BOT(1),
    POST_BOT(5),
    DEFAULT(100)
}