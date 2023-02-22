package me.taubsie.carrylogs.application.command;

import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import me.taubsie.carrylogs.application.exceptions.MustBeServerException;
import me.taubsie.carrylogs.application.exceptions.NotAllowedThereException;
import me.taubsie.carrylogs.application.service.ApplicationClassLoaderService;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.Interaction;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandInteractionOptionsProvider;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class Command {
    protected abstract void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent);

    public void execute(SlashCommandCreateEvent slashCommandCreateEvent) {
        if(isGlobal()
                && slashCommandCreateEvent.getSlashCommandInteraction().getServer().isPresent()
                && !isEnabledInServer(slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId())) {
            throw new NotAllowedThereException();
        }

        if(!isEnabledForUser(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getId())) {
            throw new MissingPermissionException();
        }

        executeCommand(slashCommandCreateEvent);
    }

    public List<SlashCommandOption> getSlashCommandOptions() {
        return Collections.emptyList();
    }

    private Optional<CommandParameters> getCommandParameters() {
        return ApplicationClassLoaderService.getInstance().getCommandParameters(getClass());
    }

    public boolean isEnabledInServer(long serverId) {
        Optional<CommandParameters> commandParameters = getCommandParameters();

        return commandParameters.isEmpty() || commandParameters.get().enabledServers().length == 0 || Arrays.stream(commandParameters.get().enabledServers()).anyMatch(value -> value == serverId);
    }

    public boolean isEnabledForUser(long userId) {
        Optional<CommandParameters> commandParameters = getCommandParameters();

        return commandParameters.isPresent() && (commandParameters.get().enabledForUsers().length == 0 || Arrays.stream(commandParameters.get().enabledForUsers()).anyMatch(value -> value == userId));
    }

    public boolean isGlobal() {
        return getEnabledServers().length == 0;
    }

    public String getCommandName() {
        return getCommandParameters().map(CommandParameters::name).orElse(null);
    }

    public long[] getEnabledServers() {
        return getCommandParameters().map(CommandParameters::enabledServers).orElse(new long[]{});
    }

    protected Server getServer(Interaction interaction) {
        Optional<Server> server = interaction.getServer();

        if(server.isEmpty()) {
            throw new MustBeServerException();
        }

        return server.get();
    }

    public String getStringOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        SlashCommandInteractionOption interactionOption = getOption(slashCommandCreateEvent, name);

        if(interactionOption.getStringValue().isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return interactionOption.getStringValue().get();
    }

    public Long getLongOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        SlashCommandInteractionOption interactionOption = getOption(slashCommandCreateEvent, name);

        if(interactionOption.getLongValue().isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return interactionOption.getLongValue().get();
    }

    public User getUserOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        SlashCommandInteractionOption interactionOption = getOption(slashCommandCreateEvent, name);

        if(interactionOption.getUserValue().isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return interactionOption.getUserValue().get();
    }

    public SlashCommandInteractionOption getOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<SlashCommandInteractionOption> interactionOption = slashCommandCreateEvent.getOptionByName(name);

        if(interactionOption.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return interactionOption.get();
    }
}