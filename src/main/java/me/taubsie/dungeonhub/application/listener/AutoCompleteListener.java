package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.command.commands.CarryDifficultyCommand;
import me.taubsie.dungeonhub.application.command.commands.CarryTierCommand;
import me.taubsie.dungeonhub.application.command.commands.CarryTypeCommand;
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTierConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ServerConnection;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.channel.Categorizable;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.AutocompleteCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOptionChoice;
import org.javacord.api.listener.interaction.AutocompleteCreateListener;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class AutoCompleteListener implements AutocompleteCreateListener {
    @Override
    public void onAutocompleteCreate(AutocompleteCreateEvent autocompleteCreateEvent) {
        try {
            Server server = autocompleteCreateEvent.getAutocompleteInteraction().getServer().orElseThrow();

            if (autocompleteCreateEvent.getAutocompleteInteraction().getFocusedOption().getName().equalsIgnoreCase(
                    CarryTypeCommand.FIELD_NAME)) {
                autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                        CarryTypeConnection.getInstance(server.getId())
                                .getAllCarryTypes()
                                .orElse(new ArrayList<>())
                                .stream()
                                .map(carryType -> SlashCommandOptionChoice.create(carryType.getDisplayName(),
                                        carryType.getIdentifier()))
                                .toList()
                );
                return;
            }

            if (autocompleteCreateEvent.getAutocompleteInteraction().getFocusedOption().getName().equalsIgnoreCase(
                    CarryTierCommand.FIELD_NAME)) {
                List<SlashCommandInteractionOption> allOptions = autocompleteCreateEvent.getAutocompleteInteraction()
                        .getOptions().stream()
                        .flatMap(option -> option.isSubcommandOrGroup() ? option.getOptions().stream() :
                                Stream.of(option))
                        .flatMap(option -> option.isSubcommandOrGroup() ? option.getOptions().stream() :
                                Stream.of(option))
                        .toList();

                Optional<SlashCommandInteractionOption> carryTypeOption = allOptions.stream()
                        .filter(option -> option.getName().equalsIgnoreCase(CarryTypeCommand.FIELD_NAME))
                        .findFirst();

                if (carryTypeOption.isPresent()) {
                    Optional<String> carryTypeIdentifier =
                            carryTypeOption.flatMap(SlashCommandInteractionOption::getStringValue);

                    if (carryTypeIdentifier.isPresent()) {
                        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                                CarryTypeConnection.getInstance(server.getId())
                                        .getByIdentifier(carryTypeIdentifier.get())
                                        .flatMap(carryType -> CarryTierConnection.getInstance(carryType).getAllCarryTiers())
                                        .orElse(List.of())
                                        .stream()
                                        .map(carryTier -> SlashCommandOptionChoice.create(carryTier.getDisplayName(),
                                                carryTier.getIdentifier()))
                                        .toList()
                        );
                        return;
                    }
                }

                throw new NoSuchElementException();
            }

            if (autocompleteCreateEvent.getAutocompleteInteraction().getFocusedOption().getName().equalsIgnoreCase(
                    CarryDifficultyCommand.FIELD_NAME)) {
                List<SlashCommandInteractionOption> allOptions = autocompleteCreateEvent.getAutocompleteInteraction()
                        .getOptions().stream()
                        .flatMap(option -> option.isSubcommandOrGroup() ? option.getOptions().stream() :
                                Stream.of(option))
                        .flatMap(option -> option.isSubcommandOrGroup() ? option.getOptions().stream() :
                                Stream.of(option))
                        .toList();

                Optional<String> carryTierIdentifier = allOptions.stream()
                        .filter(option -> option.getName().equalsIgnoreCase(CarryTierCommand.FIELD_NAME))
                        .findFirst()
                        .flatMap(SlashCommandInteractionOption::getStringValue);

                if (carryTierIdentifier.isPresent()) {
                    Optional<CarryTierModel> carryTier = allOptions.stream()
                            .filter(option -> option.getName().equalsIgnoreCase(CarryTypeCommand.FIELD_NAME))
                            .findFirst()
                            .flatMap(SlashCommandInteractionOption::getStringValue)
                            .flatMap(s -> CarryTypeConnection.getInstance(server.getId()).getByIdentifier(s))
                            .flatMap(carryType -> CarryTierConnection.getInstance(carryType)
                                    .getByIdentifier(carryTierIdentifier.get()));

                    if (carryTier.isPresent()) {
                        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                                CarryDifficultyConnection.getInstance(carryTier.get())
                                        .getAllCarryDifficulties().stream()
                                        .flatMap(Collection::stream)
                                        .map(carryDifficulty -> SlashCommandOptionChoice.create(carryDifficulty.getDisplayName(), carryDifficulty.getIdentifier()))
                                        .toList()
                        );
                        return;
                    }
                } else {
                    Optional<CarryTierModel> carryTier = autocompleteCreateEvent.getAutocompleteInteraction()
                            .getChannel()
                            .flatMap(Channel::asCategorizable)
                            .flatMap(Categorizable::getCategory)
                            .map(DiscordEntity::getId)
                            .flatMap(category -> ServerConnection.getInstance()
                                    .getCarryTierFromCategory(server.getId(), category));

                    if (carryTier.isPresent()) {
                        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                                CarryDifficultyConnection.getInstance(carryTier.get())
                                        .getAllCarryDifficulties().stream()
                                        .flatMap(Collection::stream)
                                        .map(carryDifficulty -> SlashCommandOptionChoice.create(carryDifficulty.getDisplayName(), carryDifficulty.getIdentifier()))
                                        .toList()
                        );
                        return;
                    }
                }

                throw new NoSuchElementException();
            }
        }
        catch (NoSuchElementException noSuchElementException) {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(List.of()).join();
        }
    }
}