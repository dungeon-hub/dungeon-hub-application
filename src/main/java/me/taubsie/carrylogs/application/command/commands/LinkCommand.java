package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.List;
import java.util.UUID;

@CommandParameters(name = "link", description = "Link your discord to your hypixel account.", enabledInDms = true)
public class LinkCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        UUID uuid = ConnectionService.getInstance().getUUIDByName(ign);

        String discriminatedName = ConnectionService.getInstance().getHypixelLinkedDiscord(uuid);

        if(!discriminatedName.equalsIgnoreCase(getUser().getDiscriminatedName())) {
            throw new InvalidOptionException("ign",
                    "Please add the correct discord-account to your hypixel social menu.");
        }

        //TODO database access
        respond(ApplicationService.getInstance()
                .getEmbed()
                .setTitle("Linked successfully")
                .setDescription("||Is what I would say if I had database access||")
                .setColor(EmbedColor.POSITIVE.getColor()));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = ApplicationService.getInstance().getIngamenameOption();

        return List.of(ignOption);
    }
}