package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.ServerData;
import me.taubsie.dungeonhub.application.classes.ServerProperty;
import me.taubsie.dungeonhub.application.classes.ServerPropertyType;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.ServerService;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionChoiceBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

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
                            + Arrays.stream(ServerProperty.values())
                            .filter(serverProperty -> serverProperty.isEnabled(getServer().getId()))
                            .map(ServerProperty::getName).collect(Collectors.joining(", "))
            );
        }

        if(get) {
            getConfig(property.get());
        } else {
            if(!property.get().isEnabled(getServer().getId()) && !getUser().isBotOwnerOrTeamMember()) {
                throw new InvalidOptionException("property", "This property is disabled on this server.");
            }

            Optional<String> value = slashCommandCreateEvent
                    .getSlashCommandInteraction()
                    .getOptionByIndex(0)
                    .map(option -> {
                        if(property.get().getPropertyType() == ServerPropertyType.ROLE) {
                            return getRoleOption(option, "value").getIdAsString();
                        }

                        if(property.get().getPropertyType() == ServerPropertyType.CHANNEL
                                || property.get().getPropertyType() == ServerPropertyType.CATEGORY) {
                            return getChannelOption(option, "value").getIdAsString();
                        }

                        if(property.get().getPropertyType() == ServerPropertyType.NUMBER) {
                            return String.valueOf(getLongOption(option, "value"));
                        }

                        if(property.get().getPropertyType() == ServerPropertyType.BOOLEAN) {
                            return String.valueOf(getBooleanOption(option, "value"));
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
        String oldValue = ServerService.getInstance()
                .getActualServerProperty(getServer().getId(), property)
                .map(s -> property.getPropertyType().applyPropertyType(s))
                .orElse("None was set.");

        Optional<ServerData> serverData = ServerService.getInstance().getServerData(getServer().getId());

        if(serverData.isEmpty()) {
            throw new CommandExecutionException("Couldn't load the server data from storage.");
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
            EmbedBuilder embed = ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.NEGATIVE.getColor())
                    .setDescription("No value for `" + property.getName() + "` is set.");

            if(!property.isEnabled(getServer().getId())) {
                embed.addInlineField("Option enabled", String.valueOf(property.isEnabled(getServer().getId())));
            }

            respondEphemeral(embed);
            return;
        }

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setDescription("Loaded the value of `" + property.getName() + "`.")
                .addInlineField("Current value", property.getPropertyType().applyPropertyType(value.get()));

        if(!property.isEnabled(getServer().getId())) {
            embed.addInlineField("Option enabled", String.valueOf(property.isEnabled(getServer().getId())));
        }

        respond(embed);
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

        SlashCommandOption booleanPropertyOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("property")
                .setDescription("The boolean property to choose.")
                .setChoices(Arrays.stream(ServerProperty.values())
                        .filter(serverProperty -> serverProperty.getPropertyType() == ServerPropertyType.BOOLEAN)
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

        SlashCommandOption categoryPropertyOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("property")
                .setDescription("The category property to choose.")
                .setChoices(Arrays.stream(ServerProperty.values())
                        .filter(serverProperty -> serverProperty.getPropertyType() == ServerPropertyType.CATEGORY)
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

        SlashCommandOption booleanValueOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("value")
                .setDescription("What you want to set the property to.")
                .setRequired(true)
                .build();

        SlashCommandOption channelValueOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setName("value")
                .setDescription("What you want to set the property to.")
                .setRequired(true)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .build();

        SlashCommandOption categoryValueOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setName("value")
                .setDescription("What you want to set the property to.")
                .setRequired(true)
                .setChannelTypes(List.of(ChannelType.CHANNEL_CATEGORY))
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

        SlashCommandOption setBooleanCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("set-boolean")
                .setDescription("Sets the config.")
                .setOptions(List.of(booleanPropertyOption, booleanValueOption))
                .build();

        SlashCommandOption setChannelCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("set-channel")
                .setDescription("Sets the config for channels.")
                .setOptions(List.of(channelPropertyOption, channelValueOption))
                .build();

        SlashCommandOption setCategoryCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("set-category")
                .setDescription("Sets the config for categories.")
                .setOptions(List.of(categoryPropertyOption, categoryValueOption))
                .build();

        SlashCommandOption setRoleCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("set-role")
                .setDescription("Sets the config for roles.")
                .setOptions(List.of(rolePropertyOption, roleValueOption))
                .build();

        return Arrays.asList(getCommand, setCommand, setBooleanCommand, setChannelCommand, setCategoryCommand, setRoleCommand);
    }
}