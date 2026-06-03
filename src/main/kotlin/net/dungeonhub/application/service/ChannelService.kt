package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.channel.ChannelCreateEvent
import dev.kord.core.event.channel.ChannelDeleteEvent
import dev.kord.core.event.channel.ChannelUpdateEvent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.connection.DiscordChannelConnection
import net.dungeonhub.connection.QueueConnection
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.model.discord_channel.DiscordChannelCreationModel
import net.dungeonhub.model.discord_channel.DiscordChannelUpdateModel
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@LoadExtension
class ChannelService : Extension() {
    private val logger = LoggerFactory.getLogger(BirthdayService::class.java)
    private lateinit var scheduler: Scheduler

    override val name = "channel-service"

    override suspend fun setup() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = DhScheduler()

        scheduler.schedule(30.seconds, startNow = true, name = "Send-Carry-Queue-Schedule", repeat = true) {
            sendUnnotifiedCarryQueues()
        }

        scheduler.schedule(12.hours, startNow = true, name = "Update-Channel-State-Schedule", repeat = true) {
            syncChannelNames()
        }

        event<ChannelCreateEvent> {
            check {
                event.channel is GuildChannel && event.channel !is Category
            }

            action {
                val guildId = (event.channel as GuildChannel).guildId.value.toLong()
                val channelId = event.channel.id.value.toLong()
                val channelConnection = DiscordChannelConnection[guildId].authenticated()

                val channelName = (event.channel as GuildChannel).name

                val updateModel = channelConnection.getByIdOrCreate(channelId)?.getUpdateModel() ?: return@action
                updateModel.name = channelName
                updateModel.deleted = false
                channelConnection.updateChannel(channelId, updateModel)
            }
        }

        event<ChannelUpdateEvent> {
            check {
                event.channel is GuildChannel && event.channel !is Category
            }

            action {
                val guildId = (event.channel as GuildChannel).guildId.value.toLong()
                val channelId = event.channel.id.value.toLong()
                val channelConnection = DiscordChannelConnection[guildId].authenticated()

                val oldName = (event.old as? GuildChannel)?.name
                val channelName = (event.channel as GuildChannel).name

                if(oldName == channelName) return@action

                val updateModel = channelConnection.getByIdOrCreate(channelId)?.getUpdateModel() ?: return@action
                updateModel.name = channelName
                updateModel.deleted = false
                channelConnection.updateChannel(channelId, updateModel)
            }
        }

        event<ChannelDeleteEvent> {
            check {
                event.channel is GuildChannel && event.channel !is Category
            }

            action {
                val guildId = (event.channel as GuildChannel).guildId.value.toLong()
                val channelId = event.channel.id.value.toLong()
                val channelConnection = DiscordChannelConnection[guildId].authenticated()

                val channelName = (event.channel as GuildChannel).name

                val updateModel = channelConnection.getByIdOrCreate(channelId)?.getUpdateModel() ?: return@action
                updateModel.name = channelName
                updateModel.deleted = true
                channelConnection.updateChannel(channelId, updateModel)
            }
        }
    }

    suspend fun sendUnnotifiedCarryQueues() {
        val carryQueues = QueueConnection.authenticated().getUnnotifiedQueues()?.takeIf { it.isNotEmpty() } ?: return

        for(carryQueue in carryQueues) {
            val channelId = carryQueue.relationId

            if(carryQueue.queueStep == QueueStep.Transcript && channelId != null) {
                val channel = DiscordConnection.bot.kordRef.getChannelOf<TextChannel>(Snowflake(channelId))

                if(channel != null) {
                    channel.createMessage {
                        embeds = mutableListOf(
                            ApplicationService.loadTicketNotificationFromCarryQueue(carryQueue)
                        )
                    }
                }
            }

            val updateModel = carryQueue.getUpdateModel()
            updateModel.notified = true
            QueueConnection.authenticated().updateQueue(carryQueue.id, updateModel)
        }
    }

    suspend fun syncChannelNames() {
        DiscordConnection.bot.kordRef.guilds.collect { guild ->
            val guildId = guild.id.value.toLong()
            val channelConnection = DiscordChannelConnection[guildId].authenticated()
            val channels = channelConnection.getAllChannels()?.associate { it.id to false }?.toMutableMap() ?: mutableMapOf()

            guild.channels.collect { channel ->
                if(channel is Category) {
                  return@collect
                }

                val channelId = channel.id.value.toLong()
                val channelName = channel.name

                if(channels.containsKey(channelId)) {
                    val updateModel = DiscordChannelUpdateModel(channelName, false)
                    channelConnection.updateChannel(channelId, updateModel)
                    channels[channelId] = true
                } else {
                    channelConnection.addNewChannel(
                        DiscordChannelCreationModel(
                            channelId,
                            channelName
                        )
                    )
                }
            }

            channels.filter { !it.value }.forEach { (channelId, _) ->
                val updateModel = DiscordChannelUpdateModel(null, true)
                channelConnection.updateChannel(channelId, updateModel)
                channels[channelId] = true
            }
        }
    }
}