package me.taubsie.carrylogs.application.command;

import me.taubsie.carrylogs.application.exceptions.*;
import me.taubsie.carrylogs.application.service.ApplicationClassLoaderService;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandInteractionOptionsProvider;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class Command {
    private SlashCommandCreateEvent slashCommandCreateEvent;

    protected abstract void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent);

    public void execute(SlashCommandCreateEvent slashCommandCreateEvent) {
        this.slashCommandCreateEvent = slashCommandCreateEvent;

        if(!isEnabledInServer(slashCommandCreateEvent.getSlashCommandInteraction().getServer().map(DiscordEntity::getId).orElse(0L))) {
            throw new NotAllowedThereException();
        }

        if(!hasPermissions(getUser()) || !isEnabledForUser(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getId())) {
            throw new MissingPermissionException();
        }

        executeCommand(slashCommandCreateEvent);
    }

    public EmbedBuilder getEmbed() {
        return ApplicationService.getInstance().getEmbed();
    }

    public void respond(EmbedBuilder embedBuilder) {
        slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().addEmbed(embedBuilder).respond();
    }

    public void respondEphemeral(EmbedBuilder embedBuilder) {
        slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).addEmbed(embedBuilder).respond();
    }

    public List<SlashCommandOption> getSlashCommandOptions() {
        return Collections.emptyList();
    }

    private Optional<CommandParameters> getCommandParameters() {
        return ApplicationClassLoaderService.getInstance().getCommandParameters(getClass());
    }

    private boolean hasPermissions(User user) {
        try {
            PermissionType[] permissions = getCommandParameters()
                    .map(CommandParameters::enabledForPermissions)
                    .orElse(new PermissionType[]{});
            return getServer().hasPermissions(user, permissions);
        }
        catch(MustBeServerException mustBeServerException) {
            return isEnabledInDms();
        }
    }

    public boolean isEnabledInDms() {
        return getCommandParameters().map(CommandParameters::enabledInDms).orElse(false);
    }

    public boolean isEnabledInServer(long serverId) {
        return isGlobal() || Arrays.stream(getEnabledServers()).anyMatch(value -> value == serverId);
    }

    public boolean isEnabledForUser(long userId) {
        return getEnabledUsers().length == 0 || Arrays.stream(getEnabledUsers()).anyMatch(value -> value == userId);
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

    public long[] getEnabledUsers() {
        return getCommandParameters().map(CommandParameters::enabledForUsers).orElse(new long[]{});
    }

    public Server getServer() {
        Optional<Server> server = slashCommandCreateEvent.getSlashCommandInteraction().getServer();

        if(server.isEmpty()) {
            throw new MustBeServerException();
        }

        return server.get();
    }

    public User getUser() {
        return slashCommandCreateEvent.getSlashCommandInteraction().getUser();
    }

    public String getStringOption(String name) {
        return getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public String getStringOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<String> stringValue = getOption(slashCommandCreateEvent, name).getStringValue();

        if(stringValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return stringValue.get();
    }

    public Long getLongOption(String name) {
        return getLongOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public Long getLongOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<Long> longValue = getOption(slashCommandCreateEvent, name).getLongValue();

        if(longValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return longValue.get();
    }

    public User getUserOption(String name) {
        return getUserOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public User getUserOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<User> userValue = getOption(slashCommandCreateEvent, name).getUserValue();

        if(userValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return userValue.get();
    }

    public SlashCommandInteractionOption getOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent,
                                                   String name) {
        Optional<SlashCommandInteractionOption> interactionOption = slashCommandCreateEvent.getOptionByName(name);

        if(interactionOption.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return interactionOption.get();
    }

    public void respondWithError(CommandExecutionException commandExecutionException) {
        ApplicationService.getInstance().respondWithError(slashCommandCreateEvent, commandExecutionException);
    }
}