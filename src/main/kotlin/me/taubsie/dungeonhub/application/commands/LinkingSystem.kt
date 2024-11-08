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
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.extensions.publicUserCommand
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.HypixelConnection.getHypixelLinkedDiscord
import me.taubsie.dungeonhub.application.connection.MojangConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.HelpTopic
import me.taubsie.dungeonhub.application.exceptions.*
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.*
import net.dungeonhub.connection.DiscordUserConnection
import java.util.*
import kotlin.concurrent.thread

@PrivilegedIntent
@LoadExtension
class LinkingSystem : Extension() {
    override val name = "linking-system"

    override suspend fun setup() {
        publicSlashCommand(::LinkArguments) {
            name = "link"
            description = "Link your discord to your hypixel account."
            allowInDms = true

            action {
                respond {
                    val linkedTo = DiscordUserConnection.getById(user.id.value.toLong())?.minecraftId

                    if (linkedTo != null) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.INFORMATION.color
                        embed.description = "You're already linked to user `${
                            MojangConnection.getInstance().getNameByUUID(linkedTo)
                        }`! If you think that's incorrect, try using ${"`/unlink`"}."

                        embeds = mutableListOf(
                            embed
                        )

                        return@respond
                    }

                    val linkedId = NicknameService.linkToIgn(arguments.ign, user.asUser())

                    val embed = ApplicationService.embed
                    embed.title = "Linked successfully"
                    embed.description =
                        "You're now linked to `${
                            MojangConnection.getInstance()
                                .getNameByUUID(linkedId)
                        }`."
                    embed.color = EmbedColor.POSITIVE.color

                    embeds = mutableListOf(embed)
                }

                val member = user.asMember(guild!!.id)

                val roles = RolesService.updateRoles(member)

                NicknameService.updateNickname(member, roles)
            }
        }

        listOf(693263712626278553L, 633621474183217163L, 1023684107877761196L).map { Snowflake(it) }
            .forEach { guildId ->
                publicSlashCommand(::LinkArguments) {
                    name = "manual-link"
                    description = "Manually link someone by IGN."
                    guild(guildId)
                    check {
                        failIfNot("You aren't allowed to use this command.") {
                            event.interaction.user.id.value.toLong() == 356134481452597250L
                        }
                    }

                    action {
                        respond {
                            val uuid = MojangConnection.getInstance()
                                .getUUIDByName(arguments.ign)

                            val discordUser = getHypixelLinkedDiscord(uuid)
                                .orElseThrow {
                                    InvalidOptionWarning(
                                        "ign",
                                        "Please add the correct discord-account to your hypixel social menu.\n"
                                                + "To learn more about how to do this, use `/help verification`."
                                    )
                                }

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
                            embed.color = EmbedColor.POSITIVE.color
                            embed.description = "Linked `${arguments.ign}` to: ${user.tag}"

                            embeds = mutableListOf(embed)
                        }
                    }
                }
            }

        publicSlashCommand {
            name = "sync"
            description = "Update your roles and nickname based on your linked account."
            //TODO maybe rewrite to also allow usage in dms
            allowInDms = false

            action {
                respond {
                    val userModel = DiscordUserConnection.getLinkedById(user.id.value.toLong())

                    if (userModel == null) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.NEGATIVE.color
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
                    embed.color = EmbedColor.POSITIVE.color

                    embeds = mutableListOf(embed)
                }
            }
        }

        fun respondToForceSync(target: Member): suspend FollowupMessageCreateBuilder.() -> Unit {
            return {
                val roles = RolesService.updateRoles(target)

                val embed = ApplicationService.embed

                try {
                    NicknameService.updateNickname(target, roles)

                    embed.color = EmbedColor.POSITIVE.color
                    embed.description = "Username and roles of ${target.mention} were synced!"
                } catch (notLinkedException: NotLinkedException) {
                    embed.color = EmbedColor.NEGATIVE.color
                    embed.description = "${target.mention} is not linked, their roles were synced!"
                }

                embeds = mutableListOf(embed)
            }
        }

        publicSlashCommand(::ForceSyncArguments) {
            name = "force-sync"
            description = "Forces the update of the users roles and nickname."
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
            name = "Force Sync"
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
            name = "unlink"
            description = "Unlink from your ingame-account."
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
                        MojangConnection.getInstance()
                            .getNameByUUID(oldUserModel.minecraftId)
                    }`."
                    embed.color = EmbedColor.POSITIVE.color

                    embeds = mutableListOf(embed)
                }

                val user = user.asUser()

                val roles: Map<Long, List<Role>> = RolesService.updateRoles(user)

                NicknameService.updateNickname(user, roles)
            }
        }

        publicSlashCommand(::IgnArguments) {
            name = "ign"
            description = "Shows the IGN of a linked user."

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
                        MojangConnection.getInstance().getNameByUUID(uuid)

                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.POSITIVE.color
                    embed.description = "The given user has the IGN `$ign`."
                    embed.image = "https://crafatar.com/avatars/${uuid.toString().replace("-", "")}?size=32&overlay"
                    embeds = mutableListOf(embed)
                }
            }
        }

        //TODO maybe not reuse the link argument / rename it to sth more general
        publicSlashCommand(::LinkArguments) {
            name = "find-user"
            description = "Shows which user is linked to the given IGN."

            action {
                respond {
                    val uuid = MojangConnection.getInstance().getUUIDByName(arguments.ign)

                    val userModel = DiscordUserConnection.findUserByUuid(uuid)
                        ?: throw CommandExecutionWarning("Couldn't find who the given user is linked to.")

                    addEmbed {
                        color(EmbedColor.POSITIVE)
                        description = "The given player is linked to user <@${userModel.id}>."
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(listOf("link_user", "show_help_linking").contains(event.interaction.componentId))
            }

            action {
                when (event.interaction.componentId) {
                    "link_user" -> {
                        val linkedTo =
                            DiscordUserConnection.getById(event.interaction.user.id.value.toLong())?.minecraftId

                        if (linkedTo != null) {
                            event.interaction.respondEphemeral {
                                val embed = ApplicationService.embed
                                embed.color = EmbedColor.INFORMATION.color
                                embed.description = "You're already linked to user `${
                                    MojangConnection.getInstance()
                                        .getNameByUUID(linkedTo)
                                }`! If you think that's incorrect, try using ${"`/unlink`"}."

                                embeds = mutableListOf(embed)
                            }

                            return@action
                        }

                        event.interaction.sendLinkModal()
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
                failIfNot(event.interaction.modalId == "link_ign")
            }

            action {
                val ign = event.interaction.textInputs["ign"]?.value?.let { it.ifBlank { null } }

                if (ign == null) {
                    throw InvalidOptionWarning(
                        "ign",
                        "Please enter a valid Ingame-Name."
                    )
                }

                val linkedId: UUID

                event.interaction.respondPublic {
                    linkedId = NicknameService.linkToIgn(ign, event.interaction.user)

                    val embed = ApplicationService.embed
                    embed.title = "Linked successfully"
                    embed.description = "${event.interaction.user.mention}, your UUID is now `$linkedId`"
                    embed.color = EmbedColor.POSITIVE.color

                    embeds = mutableListOf(embed)
                }

                val roles = RolesService.updateRoles(event.interaction.user)

                NicknameService.updateNickname(event.interaction.user, roles)
            }
        }

        event<MemberJoinEvent> {
            action {
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

    inner class LinkArguments : Arguments() {
        val ign by string {
            name = "ign"
            description = "The users ingame-name"
            minLength = 2
        }
    }

    inner class ForceSyncArguments : Arguments() {
        val user by user {
            name = "user"
            description = "The user to sync."
        }
    }

    inner class IgnArguments : Arguments() {
        val user by user {
            name = "user"
            description = "The user to show the IGN for."
        }
    }
}

fun ActionRowBuilder.addLinkButtons() {
    interactionButton(ButtonStyle.Primary, "link_user") {
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
