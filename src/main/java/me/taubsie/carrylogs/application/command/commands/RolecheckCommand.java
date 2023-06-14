package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.OldCarryRole;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

@CommandParameters(name = "rolecheck", description = "See for which roles you have all requirements.")
public class RolecheckCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        Optional<OldCarryRole> carryRole;

        try {
            carryRole = Optional.of(getStringOption("role")).map(OldCarryRole::valueOf);
        } catch(InvalidOptionException invalidOptionException) {
            carryRole = Optional.empty();
        }

        throw new UnknownCommandException();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = ApplicationService.getInstance().getIngamenameOption();

        SlashCommandOptionBuilder roleOption = new SlashCommandOptionBuilder()
                .setName("role")
                .setDescription("The role to check for")
                .setType(SlashCommandOptionType.STRING)
                .setRequired(false);

        for(OldCarryRole oldCarryRole : OldCarryRole.values()) {
            roleOption.addChoice(oldCarryRole.name(), oldCarryRole.name());
        }

        return List.of(ignOption, roleOption.build());
    }
}