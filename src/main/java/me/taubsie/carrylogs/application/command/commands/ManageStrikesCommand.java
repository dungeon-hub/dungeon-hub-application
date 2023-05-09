package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.InvalidSubCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.dungeonhub.common.StrikeData;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

@CommandParameters(name = "manage-strikes",
        description = "Manage the strikes of a carrier.",
        enabledForPermissions = PermissionType.MANAGE_MESSAGES)
public class ManageStrikesCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Optional<SlashCommandInteractionOption> option =
                slashCommandCreateEvent.getSlashCommandInteraction().getOptionByIndex(0);

        if(option.isEmpty()) {
            throw new InvalidSubCommandException();
        }

        switch(option.get().getName().toLowerCase()) {
            case "list-all" -> strikeList(option.get());
            case "add" -> strikeAdd(slashCommandCreateEvent, option.get());
            case "remove" -> strikeRemove(slashCommandCreateEvent, option.get());
            case "info" -> strikeInfo(slashCommandCreateEvent, option.get());
            default -> throw new InvalidSubCommandException();
        }
    }

    public void strikeList(SlashCommandInteractionOption slashCommandInteractionOption) {
        User user = getUserOption(slashCommandInteractionOption, "user");

        List<StrikeData> strikeData = ConnectionService.getInstance().loadAllStrikeData(getServer().getId(),
                user.getId());

        respondEphemeral(ApplicationService.getInstance().formatStrikes(strikeData, user));
    }

    public void strikeAdd(SlashCommandCreateEvent slashCommandCreateEvent,
                          SlashCommandInteractionOption slashCommandInteractionOption) {
        User userToStrike = getUserOption(slashCommandInteractionOption, "user");

        String reason = null;
        try {
            reason = getStringOption(slashCommandInteractionOption, "reason");
        }
        catch(InvalidOptionException ignored) {
            //ignored since then reason should just be null
        }

        StrikeData strike = new StrikeData(getServer().getId(), userToStrike.getId())
                .setReason(reason)
                .setStriker(getUser().getId());

        StrikeData sentStrike = ConnectionService.getInstance().insertStrikeData(strike);

        respond(ApplicationService.getInstance().formatStrike(sentStrike));
    }

    public void strikeInfo(SlashCommandCreateEvent slashCommandCreateEvent,
                           SlashCommandInteractionOption slashCommandInteractionOption) {
        //TODO implement
        throw new InvalidSubCommandException();
    }

    public void strikeRemove(SlashCommandCreateEvent slashCommandCreateEvent,
                             SlashCommandInteractionOption slashCommandInteractionOption) {
        //TODO implement
        throw new InvalidSubCommandException();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        //TODO add subcommands
        // > /manage-strikes list-all <user> < IN WORK
        // > /manage-strikes add <user> [reason] < IN WORK
        // > /manage-strikes info <id> < ADD AT A LATER POINT
        // > /manage-strikes remove <id> < LATER

        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to manage strikes of.")
                .setRequired(true)
                .build();

        SlashCommandOption reasonOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("reason")
                .setDescription("The reason for the action.")
                .setRequired(false)
                .setMaxLength(200)
                .build();

        SlashCommandOption listOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("list-all")
                .setDescription("List all strikes of a user.")
                .setRequired(true)
                .addOption(userOption)
                .build();

        SlashCommandOption addOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("add")
                .setDescription("Add a strike to the given user.")
                .setRequired(true)
                .addOption(userOption)
                .addOption(reasonOption)
                .build();

        return List.of(listOption, addOption);
    }
}