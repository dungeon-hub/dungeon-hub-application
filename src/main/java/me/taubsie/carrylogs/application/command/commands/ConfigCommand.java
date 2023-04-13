package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.classes.ServerData;
import me.taubsie.carrylogs.application.classes.ServerProperty;
import me.taubsie.carrylogs.application.classes.ServerPropertyType;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ServerService;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@CommandParameters(name = "config", description = "Edits the config for the server.", enabledForPermissions = {PermissionType.ADMINISTRATOR})
public class ConfigCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        boolean get = slashCommandCreateEvent.getSlashCommandInteraction().getOptionByName("get").isPresent();

        Optional<ServerProperty> property = slashCommandCreateEvent
                .getSlashCommandInteraction()
                .getOptionByIndex(0)
                .map(option -> getStringOption(option, "property"))
                .flatMap(ServerProperty::getPropertyByName);

        if(property.isEmpty()) {
            throw new InvalidOptionException(
                    "property",
                    "Please use one of the following: "
                            + Arrays.stream(ServerProperty.values()).map(ServerProperty::getName).collect(Collectors.joining(", "))
            );
        }

        if(get) {
            Optional<String> value = ServerService.getInstance().getActualServerProperty(getServer().getId(), property.get());

            if(value.isEmpty()) {
                respondEphemeral(ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.NEGATIVE.getColor())
                        .setDescription("No value for `" + property.get().getName() + "` is set."));
                return;
            }

            respond(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.INFORMATION.getColor())
                    .setDescription("Loaded the value of `" + property.get().getName() + "`.")
                    .addInlineField("Current value", property.get().getPropertyType().applyPropertyType(value.get())));
        } else {
            Optional<String> previousValue = ServerService.getInstance().getActualServerProperty(getServer().getId(), property.get());

            String oldValue = previousValue.isPresent()
                    ? property.get().getPropertyType().applyPropertyType(previousValue.get())
                    : "None was set.";

            Optional<String> value = slashCommandCreateEvent
                    .getSlashCommandInteraction()
                    .getOptionByIndex(0)
                    .map(option -> {
                        if(property.get().getPropertyType() == ServerPropertyType.ROLE) {
                            return getRoleOption(option, "value").getIdAsString();
                        }

                        if(property.get().getPropertyType() == ServerPropertyType.CHANNEL) {
                            return getChannelOption(option, "value").getIdAsString();
                        }

                        return getStringOption(option, "value");
                    })
                    .map(s -> s.replace("\\n", "\n"));

            if(value.isEmpty()) {
                throw new InvalidOptionException("value", "Please enter something.");
            }

            Optional<ServerData> serverData = ServerService.getInstance().getServerData(getServer().getId());

            if(serverData.isEmpty()) {
                throw new CommandExecutionException() {
                    @Override
                    public String getMessage() {
                        return "Couldn't load the server data from storage.";
                    }
                };
            }

            serverData.get().setConfig(property.get(), value.get());

            respond(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.POSITIVE.getColor())
                    .setDescription("Changed the value of `" + property.get().getName() + "`.")
                    .addInlineField("Old value", oldValue)
                    .addInlineField("New value", property.get().getPropertyType().applyPropertyType(value.get())));
        }
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption allPropertiesOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("property")
                .setDescription("The property to choose.")
                .setChoices(Arrays.stream(ServerProperty.values())
                        .map(ServerProperty::getName)
                        .map(s -> new SlashCommandOptionChoiceBuilder().setName(s).setValue(s).build())
                        .toList())
                .setRequired(true)
                .build();

        SlashCommandOption propertyOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("property")
                .setDescription("The property to choose.")
                .setChoices(Arrays.stream(ServerProperty.values())
                        .filter(serverProperty -> serverProperty.getPropertyType() == ServerPropertyType.STRING)
                        .map(ServerProperty::getName)
                        .map(s -> new SlashCommandOptionChoiceBuilder().setName(s).setValue(s).build())
                        .toList())
                .setRequired(true)
                .build();

        SlashCommandOption channelPropertyOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("property")
                .setDescription("The channel property to choose.")
                .setChoices(Arrays.stream(ServerProperty.values())
                        .filter(serverProperty -> serverProperty.getPropertyType() == ServerPropertyType.CHANNEL)
                        .map(ServerProperty::getName)
                        .map(s -> new SlashCommandOptionChoiceBuilder().setName(s).setValue(s).build())
                        .toList())
                .setRequired(true)
                .build();

        SlashCommandOption rolePropertyOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("property")
                .setDescription("The role property to choose.")
                .setChoices(Arrays.stream(ServerProperty.values())
                        .filter(serverProperty -> serverProperty.getPropertyType() == ServerPropertyType.ROLE)
                        .map(ServerProperty::getName)
                        .map(s -> new SlashCommandOptionChoiceBuilder().setName(s).setValue(s).build())
                        .toList())
                .setRequired(true)
                .build();

        SlashCommandOption valueOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("value")
                .setDescription("What you want to set the property to.")
                .setRequired(true)
                .build();

        SlashCommandOption channelValueOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setName("value")
                .setDescription("What you want to set the property to.")
                .setRequired(true)
                .setChannelTypes(List.of(ChannelType.CHANNEL_CATEGORY, ChannelType.SERVER_TEXT_CHANNEL))
                .build();

        SlashCommandOption roleValueOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.ROLE)
                .setName("value")
                .setDescription("What you want to set the property to.")
                .setRequired(true)
                .build();

        SlashCommandOption getCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Shows you the current config for the server.")
                .setOptions(List.of(allPropertiesOption))
                .build();

        SlashCommandOption setCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("set")
                .setDescription("Sets the config.")
                .setOptions(List.of(propertyOption, valueOption))
                .build();

        SlashCommandOption setChannelCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("set-channel")
                .setDescription("Sets the config for channels.")
                .setOptions(List.of(channelPropertyOption, channelValueOption))
                .build();

        SlashCommandOption setRoleCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("set-role")
                .setDescription("Sets the config for roles.")
                .setOptions(List.of(rolePropertyOption, roleValueOption))
                .build();

        return Arrays.asList(getCommand, setCommand, setChannelCommand, setRoleCommand);
    }
}