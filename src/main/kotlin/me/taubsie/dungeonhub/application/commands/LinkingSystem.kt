package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.*
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.requestMembers
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.Member
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.InteractionResponseCreateBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.extensions.publicUserCommand
import dev.kordex.core.i18n.toKey
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.HelpTopic
import me.taubsie.dungeonhub.application.exceptions.*
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.*
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.i18n.Translations
import net.dungeonhub.i18n.Translations.Command.FindUser
import net.dungeonhub.i18n.Translations.Command.ForceSync
import net.dungeonhub.i18n.Translations.Command.Ign
import net.dungeonhub.i18n.Translations.Command.Link
import net.dungeonhub.i18n.Translations.Command.Sync
import net.dungeonhub.i18n.Translations.Command.Unlink
import net.dungeonhub.mojang.connection.MojangConnection
import kotlin.concurrent.thread

@PrivilegedIntent
@LoadExtension
class LinkingSystem : Extension() {
    override val name = "linking-system"

    override suspend fun setup() {
        publicSlashCommand(::SingleIgnArguments) {
            name = Link.name
            description = Link.description
            allowInDms = true

            action {
                respond {
                    val linkedTo = DiscordUserConnection.getById(user.id.value.toLong())?.minecraftId

                    if (linkedTo != null) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Information.color
                        embed.description = "You're already linked to user `${
                            MojangConnection.getNameByUUID(linkedTo)
                        }`! If you think that's incorrect, try using ${"`/unlink`"}."

                        embeds = mutableListOf(
                            embed
                        )

                        return@respond
                    }

                    val linkedId = try {
                        NicknameService.linkToIgn(arguments.ign, user.asUser())
                    } catch (invalidOptionWarning: InvalidOptionWarning) {
                        embeds = mutableListOf(ApplicationService.getErrorEmbed(invalidOptionWarning))
                        actionRow {
                            addLinkHelpButton()
                        }

                        return@respond
                    } catch (hypixelLinkedToOtherWarning: HypixelLinkedToOtherWarning) {
                        embeds = mutableListOf(ApplicationService.getErrorEmbed(hypixelLinkedToOtherWarning))
                        actionRow {
                            addLinkHelpButton()
                        }

                        return@respond
                    }

                    val embed = ApplicationService.embed
                    embed.title = "Linked successfully"
                    embed.description =
                        "You're now linked to `${MojangConnection.getNameByUUID(linkedId)}`."
                    embed.color = EmbedColor.Positive.color

                    embeds = mutableListOf(embed)

                    thread(start = true) {
                        runBlocking {
                            if (guild != null) {
                                val member = user.asMember(guild!!.id)

                                val roles = RolesService.updateRoles(member)

                                NicknameService.updateNickname(member, roles)
                            } else {
                                val user = user.asUser()

                                val roles = RolesService.updateRoles(user)

                                NicknameService.updateNickname(user, roles)
                            }
                        }
                    }
                }
            }
        }

        listOf(693263712626278553L, 633621474183217163L, 1023684107877761196L).map { Snowflake(it) }
            .forEach { guildId ->
                publicSlashCommand(::SingleIgnArguments) {
                    name = "manual-link".toKey()
                    description = "Manually link someone by IGN.".toKey()
                    guild(guildId)
                    check {
                        failIfNot("You aren't allowed to use this command.") {
                            event.interaction.user.id.value.toLong() == 356134481452597250L
                        }
                    }

                    action {
                        respond {
                            val uuid = MojangConnection.getUUIDByName(arguments.ign)

                            val discordUser = HypixelApiConnection().getHypixelLinkedDiscord(uuid)
                                ?: throw InvalidOptionWarning(
                                    "ign",
                                    "Please add the correct discord-account to your hypixel social menu.\n"
                                            + "To learn more about how to do this, use `/help verification`."
                                )

                            val users = guild!!.requestMembers { query = discordUser; limit = 5 }
                                .map { it.members }
                                .toList()

                            val user = users.map { members -> members.firstOrNull { it.username == discordUser } }
                                .firstOrNull()

                            if (user == null) {
                                throw CommandExecutionException("The specified user (`$discordUser`) does not exist.")
                            }

                            NicknameService.linkToIgn(arguments.ign, user)

                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Positive.color
                            embed.description = "Linked `${arguments.ign}` to: ${user.tag}"

                            embeds = mutableListOf(embed)
                        }
                    }
                }

                publicSlashCommand(::MassSyncArguments) {
                    name = "mass-sync".toKey()
                    description = "Sync a large amount of users.".toKey()
                    guild(guildId)
                    defaultMemberPermissions = Permissions(Permission.Administrator)

                    action {
                        respond {
                            val role = arguments.role

                            val members = guild!!.withStrategy(EntitySupplyStrategy.cachingRest).members.filter {
                                it.roleIds.contains(role.id)
                            }.toList()

                            MassSyncService.usersToSync += members.map { it.id }

                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Positive.color
                            embed.description = "Added ${members.size} users to the mass-sync queue."

                            embeds = mutableListOf(embed)
                        }
                    }
                }
            }

        publicSlashCommand {
            name = Sync.name
            description = Sync.description
            //TODO maybe rewrite to also allow usage in dms
            allowInDms = false

            action {
                respond {
                    val userModel = DiscordUserConnection.getLinkedById(user.id.value.toLong())

                    if (userModel == null) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Negative.color
                        embed.description = "Please link your ingame-account before using this command again."

                        embeds = mutableListOf(embed)

                        actionRow {
                            addLinkButtons()
                        }

                        return@respond
                    }

                    val member = user.asMember(guild!!.id)

                    val roles = RolesService.updateRoles(member)

                    var nicknameChanged = true
                    try {
                        NicknameService.updateNickname(member, userModel, roles)
                    } catch (noNameSchemaWarning: NoNameSchemaWarning) {
                        nicknameChanged = false
                    } catch (notLinkedException: NotLinkedException) {
                        embeds = mutableListOf(ApplicationService.getErrorEmbed(notLinkedException))
                        return@respond
                    }

                    val embed = ApplicationService.embed
                    embed.description = "Updating your ${if (nicknameChanged) "nickname and " else ""}roles."
                    embed.color = EmbedColor.Positive.color

                    embeds = mutableListOf(embed)
                }
            }
        }

        fun respondToForceSync(target: Member): suspend FollowupMessageCreateBuilder.() -> Unit {
            return {
                val roles = RolesService.updateRoles(target, cacheExpiration = 5)

                val embed = ApplicationService.embed

                try {
                    NicknameService.updateNickname(target, roles)

                    embed.color = EmbedColor.Positive.color
                    embed.description = "Username and roles of ${target.mention} were synced!"
                } catch (notLinkedException: NotLinkedException) {
                    embed.color = EmbedColor.Negative.color
                    embed.description = "${target.mention} is not linked, their roles were synced!"
                }

                embeds = mutableListOf(embed)
            }
        }

        publicSlashCommand(::ForceSyncArguments) {
            name = ForceSync.name
            description = ForceSync.description
            defaultMemberPermissions = Permissions(Permission.ManageNicknames, Permission.ManageRoles)
            allowInDms = false

            action {
                respond {
                    val target = arguments.user.asMember(guild!!.id)

                    respondToForceSync(target)()
                }
            }
        }

        publicUserCommand {
            name = Translations.UserCommand.ForceSync.name
            allowInDms = false
            defaultMemberPermissions = Permissions(Permission.ManageNicknames, Permission.ManageRoles)

            action {
                respond {
                    val target = targetUsers.first().asMember(guild!!.id)

                    respondToForceSync(target)()
                }
            }
        }

        publicSlashCommand {
            name = Unlink.name
            description = Unlink.description
            allowInDms = true

            action {
                respond {
                    val oldUserModel = DiscordUserConnection.getLinkedById(user.id.value.toLong())
                        ?: throw NotLinkedException()

                    val updateModel = oldUserModel.getUpdateModel()
                    updateModel.minecraftId = null

                    DiscordUserConnection.updateUser(user.id.value.toLong(), updateModel)
                        ?: throw CommandExecutionException("Couldn't update your user data.")

                    val embed = ApplicationService.embed
                    embed.description = "Unlinked successfully from account `${
                        MojangConnection.getNameByUUID(oldUserModel.minecraftId!!)
                    }`."
                    embed.color = EmbedColor.Positive.color

                    embeds = mutableListOf(embed)
                }

                thread(start = true) {
                    runBlocking {
                        val user = user.asUser()

                        val roles = RolesService.updateRoles(user)

                        try {
                            NicknameService.updateNickname(user, roles)
                        } catch (_: NotLinkedException) {
                            // Do nothing
                        }
                    }
                }
            }
        }

        publicSlashCommand(::IgnArguments) {
            name = Ign.name
            description = Ign.description

            action {
                respond {
                    val uuid = DiscordUserConnection.getById(arguments.user.id.value.toLong())?.minecraftId

                    if (uuid == null) {
                        val embed = ApplicationService.errorEmbed
                        embed.description = "That user is not linked!"
                        embeds = mutableListOf(embed)
                        return@respond
                    }

                    val ign =
                        MojangConnection.getNameByUUID(uuid)

                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.Positive.color
                    embed.description = "The given user has the IGN `$ign`."
                    embed.thumbnail {
                        url = "https://visage.surgeplay.com/face/${uuid.toString().replace("-", "")}"
                    }
                    embeds = mutableListOf(embed)
                }
            }
        }

        publicSlashCommand(::SingleIgnArguments) {
            name = FindUser.name
            description = FindUser.description

            action {
                respond {
                    val uuid = MojangConnection.getUUIDByName(arguments.ign)

                    val userModel = DiscordUserConnection.findUserByUuid(uuid)
                        ?: throw CommandExecutionWarning("Couldn't find who the given user is linked to.")

                    addEmbed {
                        color(EmbedColor.Positive)
                        description = "The given player is linked to user <@${userModel.id}>."
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(
                    listOf(
                        "link_user",
                        "link_user_silent",
                        "show_help_linking"
                    ).contains(event.interaction.componentId)
                )
            }

            action {
                when (event.interaction.componentId) {
                    "link_user", "link_user_silent" -> {
                        val linkedTo =
                            DiscordUserConnection.getById(event.interaction.user.id.value.toLong())?.minecraftId

                        if (linkedTo != null) {
                            event.interaction.respondEphemeral {
                                val embed = ApplicationService.embed
                                embed.color = EmbedColor.Information.color
                                embed.description = "You're already linked to user `${
                                    MojangConnection.getNameByUUID(linkedTo)
                                }`! If you think that's incorrect, try using ${"`/unlink`"}."

                                embeds = mutableListOf(embed)
                            }

                            return@action
                        }

                        if (event.interaction.componentId == "link_user") {
                            event.interaction.sendLinkModal()
                        } else {
                            event.interaction.sendSilentLinkModal()
                        }
                    }

                    "show_help_linking" -> {
                        event.interaction.respondEphemeral {
                            val helpTopic = HelpTopic.VERIFICATION

                            val embedBuilder = EmbedBuilder()
                            embedBuilder.title = "**" + helpTopic.title + "**"

                            val helpDisplay = helpTopic.description.getDescription(
                                event.interaction.user,
                                event.interaction.getGuildOrNull()
                            )

                            embedBuilder.color = helpDisplay.embedColor.color
                            embedBuilder.description = helpDisplay.description

                            helpDisplay.fields.forEach { embedBuilder.field(it.key, false) { it.value } }

                            embeds = mutableListOf(embedBuilder)
                        }
                    }
                }
            }
        }

        event<ModalSubmitInteractionCreateEvent> {
            check {
                failIfNot(
                    event.interaction.modalId == "link_ign" || event.interaction.modalId == "link_ign_silent"
                )
            }

            action {
                val ign = event.interaction.textInputs["ign"]?.value?.let { it.ifBlank { null } }

                if (ign == null) {
                    throw InvalidOptionWarning(
                        "ign",
                        "Please enter a valid Ingame-Name."
                    )
                }

                val linkedId = NicknameService.linkToIgn(ign, event.interaction.user)

                val response: InteractionResponseCreateBuilder.() -> Unit = {
                    val embed = ApplicationService.embed
                    embed.title = "Linked successfully"
                    embed.description = "${event.interaction.user.mention}, your UUID is now `$linkedId`"
                    embed.color = EmbedColor.Positive.color

                    embeds = mutableListOf(embed)
                }

                if (event.interaction.modalId == "link_ign") {
                    event.interaction.respondPublic(response)
                } else {
                    event.interaction.respondEphemeral(response)
                }

                val roles = RolesService.updateRoles(event.interaction.user)

                NicknameService.updateNickname(event.interaction.user, roles)
            }
        }

        event<MemberJoinEvent> {
            action {
                //TODO Remove once Hypixel / Mojang API is cached
                DiscordUserConnection.getLinkedById(event.member.id.value.toLong()) ?: return@action

                thread(start = true) {
                    runBlocking {
                        val roles: List<Role> = RolesService.updateRoles(event.member)

                        NicknameService.updateNickname(event.member, roles)
                    }
                }
            }
        }
    }

    inner class SingleIgnArguments : Arguments() {
        val ign by string {
            name = "ign".toKey()
            description = "The users ingame-name".toKey()
            minLength = 2
        }
    }

    inner class ForceSyncArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "The user to sync.".toKey()
        }
    }

    inner class IgnArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "The user to show the IGN for.".toKey()
        }
    }

    inner class MassSyncArguments : Arguments() {
        val role by role {
            name = "role".toKey()
            description = "The role in which users should be synced.".toKey()
        }
    }
}

fun ActionRowBuilder.addLinkHelpButton() {
    interactionButton(ButtonStyle.Secondary, "show_help_linking") {
        emoji(ReactionEmoji.Unicode("❔"))
        label = "Help"
    }
}

fun ActionRowBuilder.addLinkButtons() {
    interactionButton(ButtonStyle.Primary, "link_user") {
        emoji(ReactionEmoji.Unicode("\uD83D\uDD17"))
        label = "Link"
    }

    addLinkHelpButton()
}

fun ActionRowBuilder.addSilentLinkButtons() {
    interactionButton(ButtonStyle.Primary, "link_user_silent") {
        emoji(ReactionEmoji.Unicode("\uD83D\uDD17"))
        label = "Link"
    }

    interactionButton(ButtonStyle.Secondary, "show_help_linking") {
        emoji(ReactionEmoji.Unicode("❔"))
        label = "Help"
    }
}

suspend fun ButtonInteraction.sendLinkModal() {
    modal("Link your ingame-account.", "link_ign") {
        actionRow {
            textInput(TextInputStyle.Short, "ign", "Ingame-Name") {
                allowedLength = 3..ApplicationService.MAX_MINECRAFT_USERNAME_LENGTH
                placeholder = "For example: Taubsie"
                required = true
            }
        }
    }
}

suspend fun ButtonInteraction.sendSilentLinkModal() {
    modal("Link your ingame-account.", "link_ign_silent") {
        actionRow {
            textInput(TextInputStyle.Short, "ign", "Ingame-Name") {
                allowedLength = 3..ApplicationService.MAX_MINECRAFT_USERNAME_LENGTH
                placeholder = "For example: Taubsie"
                required = true
            }
        }
    }
}
