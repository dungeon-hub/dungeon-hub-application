package me.taubsie.dungeonhub.application.misc

import dev.kord.core.entity.User
import dev.kord.core.entity.effectiveName
import lombok.AllArgsConstructor
import lombok.Getter
import lombok.NoArgsConstructor
import lombok.Setter
import net.dungeonhub.hypixel.connection.HypixelConnection
import net.dungeonhub.model.discord_user.DiscordUserModel
import net.dungeonhub.mojang.connection.MojangConnection

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
class PlayerInformation(private val user: User, private val discordUserModel: DiscordUserModel) {
    val replacements: Map<String, () -> String>
        get() {
            val replacements: MutableMap<String, () -> String> = HashMap()

            replacements["discord.name"] = { user.username }
            replacements["discord.displayname"] = { user.effectiveName }
            replacements["minecraft.name"] = {
                MojangConnection.getNameByUUID(discordUserModel.minecraftId!!)
            }
            replacements["skyblock.catacombs.level"] =
                { HypixelConnection.getCataLevelByUUID(discordUserModel.minecraftId!!).toString() }
            replacements["skyblock.level"] = {
                HypixelConnection.getSkyblockLevelByUUID(
                    discordUserModel.minecraftId!!
                ).stream().mapToObj { i: Int -> java.lang.String.valueOf(i) }.findAny().orElse("?")
            }

            return replacements
        }
}