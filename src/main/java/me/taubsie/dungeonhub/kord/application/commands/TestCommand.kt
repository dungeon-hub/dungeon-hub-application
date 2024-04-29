package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension

@LoadExtension
class TestCommand : Extension() {
    override val name = "test"

    override suspend fun setup() {
        publicSlashCommand(::TestArgs) {
            name = "test"
            description = "Testing Commands is so cool!"

            action {
                respond {
                    content = "Hi ${arguments.msg ?: user.mention}!"
                }
            }
        }
    }

    inner class TestArgs : Arguments() {
        val msg by optionalString {
            name = "name"
            description = "Name of the user"
        }
    }
}