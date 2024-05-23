package me.taubsie.dungeonhub.kord.application.misc

import dev.kord.core.entity.User
import dev.kord.core.entity.effectiveName
import lombok.AllArgsConstructor
import lombok.Getter
import lombok.NoArgsConstructor
import lombok.Setter
import me.taubsie.dungeonhub.application.connection.MojangConnection
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel
import me.taubsie.dungeonhub.kord.application.connection.HypixelConnection.getCataLevelByUUID
import me.taubsie.dungeonhub.kord.application.connection.HypixelConnection.getSkyblockLevelByUUID

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
                MojangConnection.getInstance()
                    .getNameByUUID(discordUserModel.minecraftId)
            }
            replacements["skyblock.catacombs.level"] =
                { getCataLevelByUUID(discordUserModel.minecraftId).toString() }
            replacements["skyblock.level"] = {
                getSkyblockLevelByUUID(
                    discordUserModel.minecraftId
                ).stream().mapToObj { i: Int -> java.lang.String.valueOf(i) }.findAny().orElse("?")
            }

            return replacements
        }
}