package me.taubsie.dungeonhub.application.misc

import dev.kord.core.entity.User
import dev.kord.core.entity.effectiveName
import lombok.AllArgsConstructor
import lombok.Getter
import lombok.NoArgsConstructor
import lombok.Setter
import me.taubsie.dungeonhub.application.exceptions.NotLinkedException
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.model.discord_user.DiscordUserModel
import net.dungeonhub.mojang.connection.MojangConnection

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
class PlayerInformation(private val user: User, private val discordUserModel: DiscordUserModel, private val cacheExpiration: Int) {
    val replacements: Map<String, () -> String>
        get() {
            val apiConnection = HypixelApiConnection().withCacheExpiration(cacheExpiration)

            val replacements: MutableMap<String, () -> String> = HashMap()

            replacements["discord.name"] = { user.username }
            replacements["discord.displayname"] = { user.effectiveName }
            replacements["minecraft.name"] = {
                MojangConnection.getNameByUUID(discordUserModel.minecraftId ?: throw NotLinkedException() )
            }
            replacements["skyblock.catacombs.level"] =
                {
                    apiConnection.getSkyblockProfiles(discordUserModel.minecraftId!!)?.profiles?.maxOfOrNull {
                        it.getCurrentMember(discordUserModel.minecraftId!!)?.dungeons?.catacombsLevel ?: 0
                    }?.toString() ?: "?"
                }
            replacements["skyblock.level"] = {
                apiConnection.getSkyblockProfiles(discordUserModel.minecraftId!!)?.profiles?.maxOfOrNull {
                    it.getCurrentMember(discordUserModel.minecraftId!!)?.leveling?.level ?: 0
                }?.toString() ?: "?"
            }

            return replacements
        }
}