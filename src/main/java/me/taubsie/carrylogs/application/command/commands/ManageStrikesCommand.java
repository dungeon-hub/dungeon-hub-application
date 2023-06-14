package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.classes.ServerProperty;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.InvalidSubCommandException;
import me.taubsie.carrylogs.application.messages.AllStrikesMessage;
import me.taubsie.carrylogs.application.messages.PageableMessage;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.StrikeData;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

@CommandParameters(name = "manage-strikes",
        description = "Manage the strikes of a carrier.",
        enabledForPermissions = PermissionType.MODERATE_MEMBERS)
public class ManageStrikesCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        //TODO LIMIT STRIKE COMMAND TO STRIKES THAT WERE ISSUED ON THE SAME SERVER
        Optional<SlashCommandInteractionOption> option =
                slashCommandCreateEvent.getSlashCommandInteraction().getOptionByIndex(0);

        if(option.isEmpty()) {
            throw new InvalidSubCommandException();
        }

        switch(option.get().getName().toLowerCase()) {
            case "list-all" -> strikeList(slashCommandCreateEvent, option.get());
            case "add" -> strikeAdd(slashCommandCreateEvent, option.get());
            case "remove" -> strikeRemove(slashCommandCreateEvent, option.get());
            case "info" -> strikeInfo(slashCommandCreateEvent, option.get());
            default -> throw new InvalidSubCommandException();
        }
    }

    public void strikeList(SlashCommandCreateEvent slashCommandCreateEvent,
                           SlashCommandInteractionOption slashCommandInteractionOption) {
        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater(true)
                .thenAccept(responseUpdater -> {
                    User user = getUserOption(slashCommandInteractionOption, "user");

                    List<StrikeData> strikeData = DungeonHubConnection.getInstance().loadAllStrikeData(getServer().getId(),
                            user.getId());

                    EmbedBuilder embed = ApplicationService.getInstance().formatStrikes(strikeData, user, 1);

                    int maxPage = DungeonHubConnection.getInstance().getMaxAllStrikePage(getServer().getId(),
                            user.getId());

                    Message message = responseUpdater
                            .addEmbed(embed)
                            .addComponents(PageableMessage.getComponents(true, maxPage == 1))
                            .update()
                            .join();

                    new AllStrikesMessage(1, message.getChannel().getId(), message.getId(), user.getId());
                });
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

        StrikeData sentStrike = DungeonHubConnection.getInstance().insertStrikeData(strike);

        respond(ApplicationService.getInstance().formatStrike(sentStrike));

        try {
            userToStrike.sendMessage(ApplicationService.getInstance().formatStrikeDM(sentStrike));

            ServerProperty.STRIKES_LOGS_CHANNEL
                    .getValue(getServer().getId())
                    .flatMap(s -> slashCommandCreateEvent.getApi().getTextChannelById(s))
                    .ifPresent(textChannel -> textChannel.sendMessage(ApplicationService.getInstance()
                            .formatStrikeLog(sentStrike)));
        }
        catch(CompletionException completionException) {
            //ignored
        }
    }

    public void strikeInfo(SlashCommandCreateEvent slashCommandCreateEvent,
                           SlashCommandInteractionOption slashCommandInteractionOption) {
        //TODO implement
        throw new InvalidSubCommandException();
    }

    public void strikeRemove(SlashCommandCreateEvent slashCommandCreateEvent,
                             SlashCommandInteractionOption slashCommandInteractionOption) {
        long id = getLongOption(slashCommandInteractionOption, "id");
        User user = getUser();

        DungeonHubConnection.getInstance().removeStrike(getServer().getId(), id);

        respondEphemeral(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Removed strike #" + id + "."));

        ServerProperty.STRIKES_LOGS_CHANNEL
                .getValue(getServer().getId())
                .flatMap(s -> slashCommandCreateEvent.getApi().getTextChannelById(s))
                .ifPresent(textChannel -> textChannel.sendMessage(ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.INFORMATION.getColor())
                        .setDescription(user.getDiscriminatedName()
                                + " removed strike #" + id + ".")));
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

        SlashCommandOption idOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("id")
                .setDescription("The id of the strike.")
                .setRequired(true)
                .setLongMinValue(1)
                .build();

        SlashCommandOption listOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("list-all")
                .setDescription("List all strikes of a user.")
                .addOption(userOption)
                .build();

        SlashCommandOption infoOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("info")
                .setDescription("Display more information about a strike. <NOT IMPLEMENTED YET>")
                .addOption(idOption)
                .build();

        SlashCommandOption addOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("add")
                .setDescription("Add a strike to the given user.")
                .addOption(userOption)
                .addOption(reasonOption)
                .build();

        SlashCommandOption removeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("remove")
                .setDescription("Remove the given strike.")
                .addOption(idOption)
                .build();

        return List.of(listOption, infoOption, addOption, removeOption);
    }
}