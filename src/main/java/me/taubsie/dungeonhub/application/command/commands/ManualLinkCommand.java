package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.kord.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@CommandParameters(name = "manual-link", description = "Manually link someone by IGN.", enabledForUsers = {356134481452597250L})
public class ManualLinkCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        UUID uuid = MojangConnection.getInstance().getUUIDByName(ign);

        String discordUser = HypixelConnection.INSTANCE.getHypixelLinkedDiscord(uuid)
                .orElseThrow(() -> new InvalidOptionException("ign", "Please add the correct discord-account to your hypixel social menu.\n"
                        + "To learn more about how to do this, use `/help verification`."));

        Set<User> users = getServer().getMembersByName(discordUser);

        if (users.size() != 1) {
            throw new CommandExecutionException("The specified user (`" + discordUser + "`) does not exist.");
        }

        users.forEach(user -> NicknameService.getInstance().linkToIgn(ign, user));

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Linked `" + ign + "` to: " + users.stream().map(User::getMentionTag).collect(Collectors.joining(", "))));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("ign")
                .setDescription("Ingame-name")
                .setRequired(true)
                .build();

        return List.of(ignOption);
    }

    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L};
    }
}