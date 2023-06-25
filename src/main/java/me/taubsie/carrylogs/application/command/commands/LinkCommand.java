package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.connection.HypixelConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "link", description = "Link your discord to your hypixel account.", enabledInDms = true)
public class LinkCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        UUID uuid = DungeonHubConnection.getInstance().getUUIDByName(ign);

        String hypixelName = HypixelConnection.getInstance().getHypixelLinkedDiscord(uuid);

        User user = getUser();
        String username = user.getDiscriminator().equals("0") ? user.getName() : user.getDiscriminatedName();

        if(!hypixelName.equalsIgnoreCase(username)) {
            throw new InvalidOptionException("ign",
                    "Please add the correct discord-account to your hypixel social menu.");
        }

        //TODO database access
        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() -> ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle("Linked successfully")
                        .setDescription("||Is what I would say if I had database access||")
                        .setColor(EmbedColor.POSITIVE.getColor())));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = ApplicationService.getInstance().getIngamenameOption();

        return List.of(ignOption);
    }
}