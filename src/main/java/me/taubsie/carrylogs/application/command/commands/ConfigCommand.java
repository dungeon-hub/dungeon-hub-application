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

@CommandParameters(name = "config", description = "Edits the config for the server.", enabledForPermissions =
        {PermissionType.ADMINISTRATOR})
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
            getConfig(property.get());
        } else {
            //TODO maybe enable if user is bot owner
            if(!property.get().isEnabled(getServer().getId())) {
                throw new InvalidOptionException("property", "Property is disabled on this server.");
            }

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

                        if(property.get().getPropertyType() == ServerPropertyType.NUMBER) {
                            return String.valueOf(getNumberOption(option, "value"));
                        }

                        return getStringOption(option, "value");
                    })
                    .map(s -> s.replace("\\n", "\n"));

            if(value.isEmpty()) {
                throw new InvalidOptionException("value", "Please enter a new value.");
            }

            setConfig(property.get(), value.get());
        }
    }

    private void setConfig(ServerProperty property, String value) {
        Optional<String> previousValue = ServerService.getInstance().getActualServerProperty(getServer().getId(),
                property);

        String oldValue = previousValue.isPresent()
                ? property.getPropertyType().applyPropertyType(previousValue.get())
                : "None was set.";

        Optional<ServerData> serverData = ServerService.getInstance().getServerData(getServer().getId());

        if(serverData.isEmpty()) {
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Couldn't load the server data from storage.";
                }
            };
        }

        serverData.get().setConfig(property, value);

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Changed the value of `" + property.getName() + "`.")
                .addInlineField("Old value", oldValue)
                .addInlineField("New value", property.getPropertyType().applyPropertyType(value)));
    }

    private void getConfig(ServerProperty property) {
        Optional<String> value = ServerService.getInstance().getActualServerProperty(getServer().getId(), property);

        if(value.isEmpty()) {
            respondEphemeral(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.NEGATIVE.getColor())
                    .setDescription("No value for `" + property.getName() + "` is set.")
                    .addInlineField("Option enabled", String.valueOf(property.isEnabled(getServer().getId()))));
            return;
        }

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setDescription("Loaded the value of `" + property.getName() + "`.")
                .addInlineField("Current value", property.getPropertyType().applyPropertyType(value.get()))
                .addInlineField("Option enabled", String.valueOf(property.isEnabled(getServer().getId()))));
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

        //TODO make autocompletable and maybe add options? idk
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