package net.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.createVoiceChannel
import dev.kord.rest.builder.channel.addMemberOverwrite
import dev.kord.rest.builder.channel.addRoleOverwrite
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.Video
import kotlinx.coroutines.CancellationException
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.i18n.Translations.Command.CreateVc
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@LoadExtension
class CreatePrivateVCCommand : Extension() {
    private val logger: Logger = LoggerFactory.getLogger(CreatePrivateVCCommand::class.java)
    override val name = "create-vc"
    override suspend fun setup() {
        listOf(693263712626278553L, 1023684107877761196L).map { Snowflake(it) }
            .forEach { guildId ->
                publicSlashCommand(::CreateVCArgs) {
                    name = CreateVc.name
                    description = CreateVc.description
                    guild(guildId)
                    defaultMemberPermissions = Permissions(Permission.ManageChannels)
                    allowInDms = false

                    action {
                        try {
                            val everyoneRole = guild!!.id

                            val rawVerifiedId = ServerProperty.VERIFIED_GROUP_ROLE.getValue(guild!!.id)
                                ?: throw CommandExecutionException("VERIFIED_GROUP_ROLE has not been configured in ServerProperty")
                            val verifiedRoleId = Snowflake(rawVerifiedId)

                            val rawStaffId = ServerProperty.STAFF_GROUP_ROLE.getValue(guild!!.id)
                                ?: throw CommandExecutionException("STAFF_GROUP_ROLE has not been configured in ServerProperty")
                            val staffRoleId = Snowflake(rawStaffId)

                            val rawAdminId = ServerProperty.ADMINISTRATION_GROUP_ROLE.getValue(guild!!.id)
                                ?: throw CommandExecutionException("ADMINISTRATION_GROUP_ROLE has not been configured in ServerProperty")
                            val adminRoleId = Snowflake(rawAdminId)

                            val rawCategoryId = ServerProperty.VC_CREATE_CATEGORY.getValue(guild!!.id)
                            val categoryId = rawCategoryId?.let { Snowflake(it) }

                            val voiceChannel = guild!!.createVoiceChannel(arguments.name) {
                                addRoleOverwrite(adminRoleId) {
                                    allowed += Permission.ViewChannel
                                    allowed += Permission.ManageChannels
                                    allowed += Permission.ManageRoles

                                    allowed += Permission.MoveMembers
                                    allowed += Permission.DeafenMembers
                                    allowed += Permission.MuteMembers

                                    allowed += Permission.ViewChannel
                                    allowed += Permission.Connect
                                    allowed += Permission.SendMessages
                                }

                                addRoleOverwrite(staffRoleId) {
                                    allowed += Permission.MoveMembers
                                    allowed += Permission.DeafenMembers
                                    allowed += Permission.MuteMembers

                                    allowed += Permission.ViewChannel
                                    allowed += Permission.Connect
                                    allowed += Permission.SendMessages
                                }

                                addRoleOverwrite(verifiedRoleId) {
                                    allowed += Permission.ViewChannel
                                    allowed += Permission.SendMessages
                                }

                                addRoleOverwrite(everyoneRole) {
                                    denied += Permission.ViewChannel
                                    denied += Permission.Connect
                                    denied += Permission.SendMessages
                                }

                                addMemberOverwrite(arguments.user.id) {
                                    allowed += Permission.ViewChannel
                                    allowed += Permission.Connect
                                    allowed += Permission.ManageChannels
                                    allowed += Permission.SendMessages
                                    allowed += Permission.Speak
                                    allowed += Permission.Video
                                    allowed += Permission.MoveMembers
                                }

                                parentId = categoryId
                            }

                            respond {
                                content = "${voiceChannel.mention} has been created successfully!"
                            }
                        } catch (cancellationException: CancellationException) {
                            throw cancellationException
                        } catch (exception: Exception) {
                            logger.error("Error while creating a private VC.", exception)
                            respond {
                                addEmbed {
                                    description = "Error while creating a private VC."
                                    color(EmbedColor.Negative)
                                }
                            }
                        }
                    }
                }
            }
    }

    class CreateVCArgs : Arguments() {
        val name by string {
            name = CreateVc.Arguments.VcName.name
            description = CreateVc.Arguments.VcName.description
        }

        val user by user {
            name = CreateVc.Arguments.VcUser.name
            description = CreateVc.Arguments.VcUser.description
        }
    }
}