package me.taubsie.dungeonhub.application.command;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTierConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.kord.application.exceptions.*;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.HighLevelComponent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandInteractionOptionsProvider;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * This class is used to allow easier implementation of commands, as a class scanner looks for classes that extends
 * from this class and that have the {@link CommandParameters} annotation.
 * They are then automatically loaded on bot start and registered on discord.
 * It also contains QOL methods to easier get command options or the user that executed the command.
 */
public abstract class Command {
    private SlashCommandCreateEvent slashCommandCreateEvent;

    protected abstract void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent);

    public final EmbedBuilder getEmbed() {
        return ApplicationService.getInstance().getEmbed();
    }

    public final void respond(EmbedBuilder embedBuilder) {
        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(embedBuilder)
                .respond();
    }

    public final void respondEphemeral(EmbedBuilder embedBuilder) {
        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(embedBuilder)
                .respond();
    }

    public final void respondEphemeral(EmbedBuilder embedBuilder, HighLevelComponent... highLevelComponents) {
        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(embedBuilder)
                .addComponents(highLevelComponents)
                .respond();
    }

    public final void respondLater(@NotNull CompletableFuture<DelayedResponse> delayedResponseFuture) {
        InteractionOriginalResponseUpdater updater = slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater()
                .join();

        delayedResponseFuture.thenAccept(delayedResponse -> executeRespondLater(updater, delayedResponse));
    }

    public final void respondLaterEphemeral(@NotNull CompletableFuture<DelayedResponse> delayedResponseFuture) {
        InteractionOriginalResponseUpdater updater = slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater(true)
                .join();

        delayedResponseFuture.thenAccept(delayedResponse -> executeRespondLater(updater, delayedResponse));
    }

    private void executeRespondLater(InteractionOriginalResponseUpdater updater, DelayedResponse delayedResponse) {
        updater.setContent(delayedResponse.getContent())
                .addEmbeds(delayedResponse.getEmbed())
                .addComponents(delayedResponse.getHighLevelComponents())
                .update();
    }

    public List<SlashCommandOption> getSlashCommandOptions() {
        return Collections.emptyList();
    }

    public final Server getServer() {
        Optional<Server> server = slashCommandCreateEvent.getSlashCommandInteraction().getServer();

        if (server.isEmpty()) {
            throw new MustBeServerException();
        }

        return server.get();
    }

    public final TextChannel getChannel() {
        Optional<TextChannel> channel = slashCommandCreateEvent.getSlashCommandInteraction().getChannel();

        if (channel.isEmpty()) {
            throw new ChannelNotFoundException();
        }

        return channel.get();
    }

    public final User getUser() {
        return slashCommandCreateEvent.getSlashCommandInteraction().getUser();
    }

    public final SlashCommandInteractionOption getOptionAtIndex(int index) {
        return getOptionAtIndex(slashCommandCreateEvent.getSlashCommandInteraction(), index);
    }

    public final SlashCommandInteractionOption getOptionAtIndex(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, int index) {
        Optional<SlashCommandInteractionOption> interactionOption = slashCommandCreateEvent.getOptionByIndex(index);

        if (interactionOption.isEmpty()) {
            throw new InvalidOptionException("at index " + index);
        }

        return interactionOption.get();
    }

    public final String getStringOption(String name) {
        return getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public final Optional<String> getOptionalStringOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        return slashCommandCreateEvent.getOptionByName(name).flatMap(SlashCommandInteractionOption::getStringValue);
    }

    public final String getStringOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<String> stringValue = getOption(slashCommandCreateEvent, name).getStringValue();

        if (stringValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return stringValue.get();
    }

    public final Optional<Boolean> getOptionalBooleanOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        return slashCommandCreateEvent.getOptionByName(name)
                .flatMap(SlashCommandInteractionOption::getBooleanValue);
    }

    public final Boolean getBooleanOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<Boolean> booleanValue = getOption(slashCommandCreateEvent, name).getBooleanValue();

        if (booleanValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return booleanValue.get();
    }

    public final Optional<ServerChannel> getOptionalChannelOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        return slashCommandCreateEvent.getOptionByName(name)
                .flatMap(SlashCommandInteractionOption::getChannelValue);
    }

    public final ServerChannel getChannelOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent,
                                                String name) {
        Optional<ServerChannel> channelValue = getOption(slashCommandCreateEvent, name).getChannelValue();

        if (channelValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return channelValue.get();
    }

    public final Optional<Role> getOptionalRoleOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent,
                                                      String name) {
        return slashCommandCreateEvent.getOptionByName(name)
                .flatMap(SlashCommandInteractionOption::getRoleValue);
    }

    public final Role getRoleOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<Role> roleValue = getOptionalRoleOption(slashCommandCreateEvent, name);

        if (roleValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return roleValue.get();
    }

    public final Optional<Long> getOptionalLongOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent,
                                                      String name) {
        return slashCommandCreateEvent.getOptionByName(name).flatMap(SlashCommandInteractionOption::getLongValue);
    }

    public final Long getLongOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<Long> longValue = getOption(slashCommandCreateEvent, name).getLongValue();

        if (longValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return longValue.get();
    }

    public final User getUserOption(String name) {
        return getUserOption(slashCommandCreateEvent.getSlashCommandInteraction(), name);
    }

    public final User getUserOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent, String name) {
        Optional<User> userValue = getOption(slashCommandCreateEvent, name).getUserValue();

        if (userValue.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return userValue.get();
    }

    public final SlashCommandInteractionOption getOption(SlashCommandInteractionOptionsProvider slashCommandCreateEvent,
                                                         String name) {
        Optional<SlashCommandInteractionOption> interactionOption = slashCommandCreateEvent.getOptionByName(name);

        if (interactionOption.isEmpty()) {
            throw new InvalidOptionException(name);
        }

        return interactionOption.get();
    }

    public final void respondWithError(CommandExecutionException commandExecutionException) {
        ApplicationService.getInstance().respondWithError(slashCommandCreateEvent.getInteraction(),
                commandExecutionException);
    }

    public final CarryTypeModel getCarryType(long server, String identifier) {
        return CarryTypeConnection.getInstance(server)
                .getByIdentifier(identifier)
                .orElseThrow(CarryTypeNotFoundException::new);
    }

    public final CarryTierModel getCarryTier(CarryTypeModel carryTypeModel, String identifier) {
        return CarryTierConnection.getInstance(carryTypeModel)
                .getByIdentifier(identifier)
                .orElseThrow(CarryTierNotFoundException::new);
    }

    public final CarryDifficultyModel getCarryDifficulty(CarryTierModel carryTierModel, String identifier) {
        return CarryDifficultyConnection.getInstance(carryTierModel)
                .getByIdentifier(identifier)
                .orElseThrow(CarryDifficultyNotFoundException::new);
    }
}