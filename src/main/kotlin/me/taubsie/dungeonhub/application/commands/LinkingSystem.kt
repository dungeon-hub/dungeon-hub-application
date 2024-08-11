package me.taubsie.dungeonhub.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.extensions.publicUserCommand
import dev.kord.common.entity.*
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.requestMembers
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.Member
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import me.taubsie.dungeonhub.application.connection.HypixelConnection.getHypixelLinkedDiscord
import me.taubsie.dungeonhub.application.connection.MojangConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.HelpTopic
import me.taubsie.dungeonhub.application.exceptions.*
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.NicknameService
import me.taubsie.dungeonhub.application.service.RolesService
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserUpdateModel
import java.util.*

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
                    val linkedTo =
                        DiscordUserConnection.getInstance()
                            .getById(user.id.value.toLong()).map { it.minecraftId }

                    if (linkedTo.isPresent) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.INFORMATION.color
                        embed.description = "You're already linked to user `${
                            MojangConnection.getInstance()
                                .getNameByUUID(linkedTo.get())
                        }!` If you think that's incorrect, try using ${"`/unlink`"}."

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

        listOf(693263712626278553L, 633621474183217163L, 1023684107877761196L).forEach { guildId ->
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
                                InvalidOptionException(
                                    "ign",
                                    "Please add the correct discord-account to your hypixel social menu.\n"
                                            + "To learn more about how to do this, use `/help verification`."
                                )
                            }

                        val users = guild!!.requestMembers { query = discordUser; limit = 5 }
                            .map { it.members }
                            .toList()

                        val user = users.map { members -> members.firstOrNull { it.username == discordUser } }.firstOrNull()

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
                    val userModel: DiscordUserModel? =
                        DiscordUserConnection.getInstance()
                            .getById(user.id.value.toLong())
                            .filter { Objects.nonNull(it.minecraftId) }
                            .orElse(null)

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
                    } catch (noNameSchemaException: NoNameSchemaException) {
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
                    val oldUserModel =
                        DiscordUserConnection.getInstance()
                            .getLinkedById(user.id.value.toLong())
                            .orElseThrow { NotLinkedException() }

                    DiscordUserConnection.getInstance()
                        .updateUser(user.id.value.toLong(), DiscordUserUpdateModel(true))
                        .orElseThrow { CommandExecutionException("Couldn't update your user data.") }

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

        publicSlashCommand(::SendLinkMessageArguments) {
            name = "send-link-message"
            description = "Sends a message with components that are there to make linking easier."
            defaultMemberPermissions = Permissions(Permission.ManageMessages)
            allowInDms = false

            action {
                respond {
                    val channel = arguments.channel.asChannelOfOrNull<GuildMessageChannel>()
                        ?: throw CommandExecutionException("Channel couldn't be found or isn't a message channel. Please let an administrator know.")

                    channel.createMessage {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.DEFAULT.color
                        embed.title = "Linking"
                        embed.description =
                            "Please link to your Minecraft account using the buttons below.\nRemember to never give out the email connected to your Microsoft account and to never click any links!\n\nCheck out this video if you're still unsure if messages similar to this are legit: https://youtu.be/WRRIOkM8oe8?t=743&si=oc71yA9h-XJUsGpX"
                        embeds = mutableListOf(embed)

                        actionRow {
                            addLinkButtons()
                        }
                    }

                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.POSITIVE.color
                    embed.description = "Trying to send message..."
                    embeds = mutableListOf(embed)
                }
            }
        }

        publicSlashCommand(::IgnArguments) {
            name = "ign"
            description = "Shows the IGN of a linked user."

            action {
                respond {
                    val uuid =
                        DiscordUserConnection.getInstance()
                            .getById(arguments.user.id.value.toLong()).map { it.minecraftId }
                            .orElse(null)

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

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(listOf("link_user", "show_help_linking").contains(event.interaction.componentId))
            }

            action {
                when (event.interaction.componentId) {
                    "link_user" -> {
                        val linkedTo =
                            DiscordUserConnection.getInstance()
                                .getById(event.interaction.user.id.value.toLong())
                                .map { it.minecraftId }

                        if (linkedTo.isPresent) {
                            event.interaction.respondEphemeral {
                                val embed = ApplicationService.embed
                                embed.color = EmbedColor.INFORMATION.color
                                embed.description = "You're already linked to user `${
                                    MojangConnection.getInstance()
                                        .getNameByUUID(linkedTo.get())
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

    inner class SendLinkMessageArguments : Arguments() {
        val channel by channel {
            name = "channel"
            description = "The channel to send the message into."
            requiredChannelTypes = mutableSetOf(
                ChannelType.GuildText,
                ChannelType.GuildVoice,
                ChannelType.PublicGuildThread
            )
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
