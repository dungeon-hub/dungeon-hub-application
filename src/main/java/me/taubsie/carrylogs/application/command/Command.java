package me.taubsie.carrylogs.application.command;

import me.taubsie.carrylogs.application.exceptions.*;
import me.taubsie.carrylogs.application.service.ApplicationClassLoaderService;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Role;
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

/**
 * This class is used to allow easier implementation of commands, as a class scanner looks for classes that extends
 * from this class and that have the {@link CommandParameters} annotation.
 * They are then automatically loaded on bot start and registered on discord.
 * It also contains QOL methods to easier get command options or the user that executed the command.
 */
public abstract class Command {
    private SlashCommandCreateEvent slashCommandCreateEvent;

    protected abstract void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent);

    public final void execute(SlashCommandCreateEvent slashCommandCreateEvent) {
        this.slashCommandCreateEvent = slashCommandCreateEvent;

        if(!isEnabledInServer(slashCommandCreateEvent.getSlashCommandInteraction().getServer().map(DiscordEntity::getId).orElse(0L))) {
            throw new NotAllowedThereException();
        }

        if(!hasPermissions(getUser()) || !isEnabledForUser(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getId())) {
            throw new MissingPermissionException();
        }

        executeCommand(slashCommandCreateEvent);
    }

    public final EmbedBuilder getEmbed() {
        return ApplicationService.getInstance().getEmbed();
    }

    public final void respond(EmbedBuilder embedBuilder) {
        slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().addEmbed(embedBuilder).respond();
    }

    public final void respondEphemeral(EmbedBuilder embedBuilder) {
        slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).addEmbed(embedBuilder).respond();
    }

    public List<SlashCommandOption> getSlashCommandOptions() {
        return Collections.emptyList();
    }

    public long[] getEnabledServers() {
        return new long[]{};
    }

    private Optional<CommandParameters> getCommandParameters() {
        return ApplicationClassLoaderService.getInstance().getCommandParameters(getClass());
    }

    private boolean hasPermissions(User user) {
        try {
            PermissionType[] permissions = getCommandParameters()
                    .map(CommandParameters::enabledForPermissions)
                    .orElse(new PermissionType[]{});
            return getServer().hasPermissions(user, permissions) || getServer().isAdmin(user) || getServer().isOwner(user);
        }
        catch(MustBeServerException mustBeServerException) {
            return isEnabledInDms();
        }
    }

    public final boolean isEnabledInDms() {
        return getCommandParameters().map(CommandParameters::enabledInDms).orElse(false);
    }

    public final boolean isEnabledInServer(long serverId) {
        return isGlobal() || Arrays.stream(getEnabledServers()).anyMatch(value -> value == serverId);
    }

    public final boolean isEnabledForUser(long userId) {
        return getEnabledUsers().length == 0 || Arrays.stream(getEnabledUsers()).anyMatch(value -> value == userId);
    }

    public final boolean isGlobal() {
        return getEnabledServers().length == 0;
    }

    public final String getCommandName() {
        return getCommandParameters().map(CommandParameters::name).orElse(null);
    }

    public final long[] getEnabledUsers() {
        return getCommandParameters().map(CommandParameters::enabledForUsers).orElse(new long[]{});
    }

    public final Server getServer() {
        Optional<Server> server = slashCommandCreateEvent.getSlashCommandInteraction().getServer();

        if(server.isEmpty()) {
            throw new MustBeServerException();
        }

        return server.get();
    }

    public final User getUser() {
        return slashCommandCreateEvent.getSlashCommandInteraction().getUser();
    }

    public final String getStringOption(String name) {
        return getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public final String getStringOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<String> stringValue = getOption(slashCommandCreateEvent, name).getStringValue();

        if(stringValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return stringValue.get();
    }

    public final ServerChannel getChannelOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent,
                                                String name) {
        Optional<ServerChannel> channelValue = getOption(slashCommandCreateEvent, name).getChannelValue();

        if(channelValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return channelValue.get();
    }

    public final Role getRoleOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<Role> roleValue = getOption(slashCommandCreateEvent, name).getRoleValue();

        if(roleValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return roleValue.get();
    }

    public final Long getLongOption(String name) {
        return getLongOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public final Long getLongOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<Long> longValue = getOption(slashCommandCreateEvent, name).getLongValue();

        if(longValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return longValue.get();
    }

    public final User getUserOption(String name) {
        return getUserOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public final User getUserOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<User> userValue = getOption(slashCommandCreateEvent, name).getUserValue();

        if(userValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return userValue.get();
    }

    public final SlashCommandInteractionOption getOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent,
                                                         String name) {
        Optional<SlashCommandInteractionOption> interactionOption = slashCommandCreateEvent.getOptionByName(name);

        if(interactionOption.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return interactionOption.get();
    }

    public final void respondWithError(CommandExecutionException commandExecutionException) {
        ApplicationService.getInstance().respondWithError(slashCommandCreateEvent, commandExecutionException);
    }
}