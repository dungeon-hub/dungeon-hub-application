package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.rest.builder.message.EmbedBuilder
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.enums.ServerProperty
import me.taubsie.dungeonhub.kord.application.enums.ServerPropertyType
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import me.taubsie.dungeonhub.kord.application.service.ServerService
import java.util.stream.Collectors

@LoadExtension
class ConfigCommand : Extension() {
    override val name = "config-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "config"
            description = "Edits the config for the server."
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::GetArguments) {
                name = "get"
                description = "Shows you the current config for the server."

                action {
                    respond {
                        val guildId = guild!!.id.value.toLong()

                        val property = arguments.getProperty(guildId)
                        val value = ServerService.getActualServerProperty(guildId, property).orElse(null)

                        if (value == null) {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.NEGATIVE.color
                            embed.description = "No value for `${property.name}` is set."

                            if (!property.isEnabled(guildId)) {
                                embed.field("Option enabled", true) {
                                    property.isEnabled(guildId).toString()
                                }
                            }

                            embeds = mutableListOf(embed)
                            return@respond
                        }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.INFORMATION.color
                        embed.description = "Loaded the value of `${property.name}`."
                        embed.field("Current value", true) { property.propertyType.applyPropertyType(value) }

                        if (!property.isEnabled(guildId)) {
                            embed.field("Option enabled", true) {
                                property.isEnabled(guildId).toString()
                            }
                        }

                        embeds = mutableListOf(embed)
                    }
                }
            }

            group("set") {
                description = "Sets a new config property"

                publicSubCommand(::SetStringArguments) {
                    name = "string"
                    description = "Sets a config value."

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            embeds = mutableListOf(setConfig(arguments.getProperty(guildId), arguments.value, guildId))
                        }
                    }
                }

                publicSubCommand(::SetNumberArguments) {
                    name = "number"
                    description = "Sets a config value."

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            embeds = mutableListOf(
                                setConfig(
                                    arguments.getProperty(guildId),
                                    arguments.value.toString(),
                                    guildId
                                )
                            )
                        }
                    }
                }

                publicSubCommand(::SetBooleanArguments) {
                    name = "boolean"
                    description = "Sets a config value."

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            embeds = mutableListOf(
                                setConfig(
                                    arguments.getProperty(guildId),
                                    arguments.value.toString(),
                                    guildId
                                )
                            )
                        }
                    }
                }

                publicSubCommand(::SetChannelArguments) {
                    name = "channel"
                    description = "Sets a config value."

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            embeds = mutableListOf(
                                setConfig(
                                    arguments.getProperty(guildId),
                                    arguments.value.id.value.toString(),
                                    guildId
                                )
                            )
                        }
                    }
                }

                publicSubCommand(::SetCategoryArguments) {
                    name = "category"
                    description = "Sets a config value."

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            embeds = mutableListOf(
                                setConfig(
                                    arguments.getProperty(guildId),
                                    arguments.value.id.toString(),
                                    guildId
                                )
                            )
                        }
                    }
                }

                publicSubCommand(::SetRoleArguments) {
                    name = "role"
                    description = "Sets a config value."

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            embeds = mutableListOf(
                                setConfig(
                                    arguments.getProperty(guildId),
                                    arguments.value.id.toString(),
                                    guildId
                                )
                            )
                        }
                    }
                }

                publicSubCommand(::GetArguments) {
                    name = "null"
                    description = "Sets a property to null (resetting it)."

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            val property = arguments.getProperty(guildId)

                            if (!property.isEnabled(guildId)) {
                                throw InvalidOptionException("property", "This property is disabled on this server.")
                            }

                            val oldValue = ServerService.getActualServerProperty(guildId, property)
                                .map { property.propertyType.applyPropertyType(it) }
                                .orElse("None was set.")

                            val serverData = ServerService.getServerData(guildId)

                            if (serverData.isEmpty) {
                                throw CommandExecutionException("Couldn't load the server data from storage.")
                            }

                            serverData.get().setConfig(property, null)

                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.POSITIVE.color
                            embed.description = "Changed the value of `${property.name}`."
                            embed.field("Old value", true) {
                                oldValue
                            }
                            embed.field("New value", true) {
                                "None"
                            }

                            embeds = mutableListOf(embed)
                        }
                    }
                }
            }
        }
    }

    fun setConfig(property: ServerProperty, value: String, guildId: Long): EmbedBuilder {
        if (!property.isEnabled(guildId)) {
            throw InvalidOptionException("property", "This property is disabled on this server.")
        }

        val value = value.replace("\\n", "\n")

        if (value.isBlank()) {
            throw InvalidOptionException("value", "Please enter a new value.")
        }

        val oldValue = ServerService.getActualServerProperty(guildId, property)
            .map { property.propertyType.applyPropertyType(it) }
            .orElse("None was set.")

        val serverData = ServerService.getServerData(guildId)

        if (serverData.isEmpty) {
            throw CommandExecutionException("Couldn't load the server data from storage.")
        }

        serverData.get().setConfig(property, value)

        val embed = ApplicationService.embed
        embed.color = EmbedColor.POSITIVE.color
        embed.description = "Changed the value of `${property.name}`."
        embed.field("Old value", true) {
            oldValue
        }
        embed.field("New value", true) {
            property.propertyType.applyPropertyType(value)
        }

        return embed
    }

    inner class GetArguments : Arguments() {
        val property by stringChoice {
            name = "property"
            description = "The property to choose."
            choices = ServerProperty.entries.map { it.name to it.name }.toMap().toMutableMap()
        }

        fun getProperty(serverId: Long): ServerProperty {
            val property = ServerProperty.getPropertyByName(property).orElse(null)
                ?: throw InvalidOptionException(
                    "property",
                    "Please use one of the following: ${
                        ServerProperty.entries
                            .filter { it.isEnabled(serverId) }
                            .map { it.name }.stream()
                            .collect(Collectors.joining(", "))
                    }"
                )

            return property
        }
    }

    inner class SetStringArguments : Arguments() {
        val property by stringChoice {
            name = "property"
            description = "The string property to choose."
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.STRING }
                .map { it.name to it.name }
                .toMap().toMutableMap()
        }

        val value by string {
            name = "value"
            description = "What you want to set the property to."
        }

        fun getProperty(serverId: Long): ServerProperty {
            val property = ServerProperty.getPropertyByName(property).orElse(null)
                ?: throw InvalidOptionException(
                    "property",
                    "Please use one of the following: ${
                        ServerProperty.entries
                            .filter { it.isEnabled(serverId) }
                            .map { it.name }.stream()
                            .collect(Collectors.joining(", "))
                    }"
                )

            return property
        }
    }

    inner class SetNumberArguments : Arguments() {
        val property by stringChoice {
            name = "property"
            description = "The number property to choose."
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.NUMBER }
                .map { it.name to it.name }
                .toMap().toMutableMap()
        }

        val value by long {
            name = "value"
            description = "What you want to set the property to."
        }

        fun getProperty(serverId: Long): ServerProperty {
            val property = ServerProperty.getPropertyByName(property).orElse(null)
                ?: throw InvalidOptionException(
                    "property",
                    "Please use one of the following: ${
                        ServerProperty.entries
                            .filter { it.isEnabled(serverId) }
                            .map { it.name }.stream()
                            .collect(Collectors.joining(", "))
                    }"
                )

            return property
        }
    }

    inner class SetBooleanArguments : Arguments() {
        val property by stringChoice {
            name = "property"
            description = "The boolean property to choose."
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.BOOLEAN }
                .map { it.name to it.name }
                .toMap().toMutableMap()
        }

        val value by boolean {
            name = "value"
            description = "What you want to set the property to."
        }

        fun getProperty(serverId: Long): ServerProperty {
            val property = ServerProperty.getPropertyByName(property).orElse(null)
                ?: throw InvalidOptionException(
                    "property",
                    "Please use one of the following: ${
                        ServerProperty.entries
                            .filter { it.isEnabled(serverId) }
                            .map { it.name }.stream()
                            .collect(Collectors.joining(", "))
                    }"
                )

            return property
        }
    }

    inner class SetChannelArguments : Arguments() {
        val property by stringChoice {
            name = "property"
            description = "The channel property to choose."
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.CHANNEL }
                .map { it.name to it.name }
                .toMap().toMutableMap()
        }

        val value by channel {
            name = "value"
            description = "What you want to set the property to."
            requiredChannelTypes = mutableSetOf(
                ChannelType.GuildText
            )
        }

        fun getProperty(serverId: Long): ServerProperty {
            val property = ServerProperty.getPropertyByName(property).orElse(null)
                ?: throw InvalidOptionException(
                    "property",
                    "Please use one of the following: ${
                        ServerProperty.entries
                            .filter { it.isEnabled(serverId) }
                            .map { it.name }.stream()
                            .collect(Collectors.joining(", "))
                    }"
                )

            return property
        }
    }

    inner class SetCategoryArguments : Arguments() {
        val property by stringChoice {
            name = "property"
            description = "The category property to choose."
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.CATEGORY }
                .map { it.name to it.name }
                .toMap().toMutableMap()
        }

        val value by channel {
            name = "value"
            description = "What you want to set the property to."
            requiredChannelTypes = mutableSetOf(
                ChannelType.GuildCategory
            )
        }

        fun getProperty(serverId: Long): ServerProperty {
            val property = ServerProperty.getPropertyByName(property).orElse(null)
                ?: throw InvalidOptionException(
                    "property",
                    "Please use one of the following: ${
                        ServerProperty.entries
                            .filter { it.isEnabled(serverId) }
                            .map { it.name }.stream()
                            .collect(Collectors.joining(", "))
                    }"
                )

            return property
        }
    }

    inner class SetRoleArguments : Arguments() {
        val property by stringChoice {
            name = "property"
            description = "The role property to choose."
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.ROLE }
                .map { it.name to it.name }
                .toMap().toMutableMap()
        }

        val value by role {
            name = "value"
            description = "What you want to set the property to."
        }

        fun getProperty(serverId: Long): ServerProperty {
            val property = ServerProperty.getPropertyByName(property).orElse(null)
                ?: throw InvalidOptionException(
                    "property",
                    "Please use one of the following: ${
                        ServerProperty.entries
                            .filter { it.isEnabled(serverId) }
                            .map { it.name }.stream()
                            .collect(Collectors.joining(", "))
                    }"
                )

            return property
        }
    }
}