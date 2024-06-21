package me.taubsie.dungeonhub.kord.application.exceptions

class HypixelLinkedToOtherException : CommandExecutionException {
    constructor(ign: String) : super(
        """
    `$ign` has their ingame discord-account set to something else.
    If you need more information about linking your discord account, please use `/help verification`
    """.trimIndent()
    )

    constructor(ign: String, wrongUsername: String, actualUsername: String) : super(
        """
    `$ign` has their ingame discord-account set to `$wrongUsername`.
    If that is your account, please change it to `$actualUsername` and wait for it to update.
    If you need more information about linking your discord account, please use `/help verification`
    """.trimIndent()
    )
}