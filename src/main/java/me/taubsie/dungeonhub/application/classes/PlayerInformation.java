package me.taubsie.dungeonhub.application.classes;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.taubsie.dungeonhub.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import org.javacord.api.entity.user.User;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerInformation {
    private User user;
    private DiscordUserModel discordUserModel;

    public Map<String, Supplier<String>> getReplacements() {
        Map<String, Supplier<String>> replacements = new HashMap<>();

        replacements.put("discord.name", () -> user.getName());
        //TODO use display name as soon as javacord catches on
        replacements.put("discord.displayname", () -> user.getName());
        replacements.put("minecraft.name", () -> MojangConnection.getInstance()
                .getNameByUUID(discordUserModel.getMinecraftId()));
        replacements.put("skyblock.catacombs.level", () -> String.valueOf(HypixelConnection.getInstance()
                .getCataLevelByUUID(discordUserModel.getMinecraftId())));
        replacements.put("skyblock.level", () -> String.valueOf(HypixelConnection.getInstance().getSkyblockLevelByUUID(discordUserModel.getMinecraftId())));

        return replacements;
    }
}