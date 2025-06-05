package net.dungeonhub.application.commands

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.enums.ServerPropertyType
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.ServerService
import net.dungeonhub.i18n.Translations.Command.Config
import java.util.stream.Collectors

/**
 * The config command allows you to edit the config for the server.
 * This includes setting and getting values for the server.
 * The command is only available for users with the Administrator permission and is also only available in guilds.
 * The command has the following subcommands:
 * - `get`: Shows you the current config for the server.
 * - `set string`: Sets a string config value.
 * - `set number`: Sets a number config value.
 * - `set boolean`: Sets a boolean config value.
 * - `set channel`: Sets a channel config value.
 * - `set category`: Sets a category config value.
 * - `set role`: Sets a role config value.
 * - `set null`: Sets a property to null (resetting it).
 * @see ServerProperty
 */
@LoadExtension
class ConfigCommand : Extension() {
    override val name = "config-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = Config.name
            description = Config.description
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::GetArguments) {
                name = "get".toKey()
                description = "Shows you the current config for the server.".toKey()

                action {
                    respond {
                        val guildId = guild!!.id.value.toLong()

                        val property = arguments.getProperty(guildId)
                        val value = ServerService.getActualServerProperty(guildId, property).orElse(null)

                        if (value == null) {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Negative.color
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
                        embed.color = EmbedColor.Information.color
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

            group("set".toKey()) {
                description = "Sets a new config property".toKey()

                publicSubCommand(::SetStringArguments) {
                    name = "string".toKey()
                    description = "Sets a config value.".toKey()

                    action {
                        respond {
                            val guildId = guild!!.id.value.toLong()

                            embeds = mutableListOf(setConfig(arguments.getProperty(guildId), arguments.value, guildId))
                        }
                    }
                }

                publicSubCommand(::SetNumberArguments) {
                    name = "number".toKey()
                    description = "Sets a config value.".toKey()

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
                    name = "boolean".toKey()
                    description = "Sets a config value.".toKey()

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
                    name = "channel".toKey()
                    description = "Sets a config value.".toKey()

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
                    name = "category".toKey()
                    description = "Sets a config value.".toKey()

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
                    name = "role".toKey()
                    description = "Sets a config value.".toKey()

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
                    name = "null".toKey()
                    description = "Sets a property to null (resetting it).".toKey()

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
                            embed.color = EmbedColor.Positive.color
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

    fun setConfig(property: ServerProperty, newValue: String, guildId: Long): EmbedBuilder {
        if (!property.isEnabled(guildId)) {
            throw InvalidOptionException("property", "This property is disabled on this server.")
        }

        val value = newValue.replace("\\n", "\n")

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
        embed.color = EmbedColor.Positive.color
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
            name = "property".toKey()
            description = "The property to choose.".toKey()
            choices = ServerProperty.entries.map { it.name.toKey() to it.name }.toMap().toMutableMap()
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
            name = "property".toKey()
            description = "The string property to choose.".toKey()
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.STRING }
                .map { it.name.toKey() to it.name }
                .toMap().toMutableMap()
        }

        val value by string {
            name = "value".toKey()
            description = "What you want to set the property to.".toKey()
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
            name = "property".toKey()
            description = "The number property to choose.".toKey()
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.NUMBER }
                .map { it.name.toKey() to it.name }
                .toMap().toMutableMap()
        }

        val value by long {
            name = "value".toKey()
            description = "What you want to set the property to.".toKey()
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
            name = "property".toKey()
            description = "The boolean property to choose.".toKey()
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.BOOLEAN }
                .map { it.name.toKey() to it.name }
                .toMap().toMutableMap()
        }

        val value by boolean {
            name = "value".toKey()
            description = "What you want to set the property to.".toKey()
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
            name = "property".toKey()
            description = "The channel property to choose.".toKey()
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.CHANNEL }
                .map { it.name.toKey() to it.name }
                .toMap().toMutableMap()
        }

        val value by channel {
            name = "value".toKey()
            description = "What you want to set the property to.".toKey()
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
            name = "property".toKey()
            description = "The category property to choose.".toKey()
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.CATEGORY }
                .map { it.name.toKey() to it.name }
                .toMap().toMutableMap()
        }

        val value by channel {
            name = "value".toKey()
            description = "What you want to set the property to.".toKey()
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
            name = "property".toKey()
            description = "The role property to choose.".toKey()
            choices = ServerProperty.entries
                .filter { it.propertyType == ServerPropertyType.ROLE }
                .map { it.name.toKey() to it.name }
                .toMap().toMutableMap()
        }

        val value by role {
            name = "value".toKey()
            description = "What you want to set the property to.".toKey()
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