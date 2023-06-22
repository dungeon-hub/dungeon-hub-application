package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.common.CarryTier;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.channel.Categorizable;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.AutocompleteCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOptionChoice;
import org.javacord.api.listener.interaction.AutocompleteCreateListener;

import java.util.*;

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
                    "carry-type")) {
                autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                        DungeonHubConnection.getInstance()
                                .loadCarryTypesForServer(server.getId())
                                .stream()
                                .map(carryType -> SlashCommandOptionChoice.create(carryType.getDisplayName(),
                                        carryType.getIdentifier()))
                                .toList()
                );
                return;
            }

            if (autocompleteCreateEvent.getAutocompleteInteraction().getFocusedOption().getName().equalsIgnoreCase(
                    "carry-tier")) {
                if (autocompleteCreateEvent.getAutocompleteInteraction().getOptionByName("carry-type").isPresent()) {
                    Optional<String> carryTypeIdentifier =
                            autocompleteCreateEvent.getAutocompleteInteraction()
                                    .getOptionByName("carry-type")
                                    .flatMap(SlashCommandInteractionOption::getStringValue);

                    if (carryTypeIdentifier.isPresent()) {
                        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                                DungeonHubConnection.getInstance()
                                        .loadCarryType(server.getId(), carryTypeIdentifier.get())
                                        .map(carryType -> DungeonHubConnection.getInstance().loadCarryTiers(carryType))
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
                    "carry-difficulty")) {
                Optional<String> carryTierIdentifier = autocompleteCreateEvent.getAutocompleteInteraction()
                        .getOptionByName("carry-tier")
                        .flatMap(SlashCommandInteractionOption::getStringValue);


                if (carryTierIdentifier.isPresent()) {
                    Optional<CarryTier> carryTier = autocompleteCreateEvent.getAutocompleteInteraction()
                            .getOptionByName("carry-type")
                            .flatMap(SlashCommandInteractionOption::getStringValue)
                            .flatMap(s -> DungeonHubConnection.getInstance().loadCarryType(server.getId(), s))
                            .flatMap(carryType -> DungeonHubConnection.getInstance().loadCarryTier(carryType,
                                    carryTierIdentifier.get()))
                            .or(() -> autocompleteCreateEvent.getAutocompleteInteraction()
                                    .getChannel()
                                    .flatMap(Channel::asCategorizable)
                                    .flatMap(Categorizable::getCategory)
                                    .map(DiscordEntity::getId)
                                    .flatMap(category -> DungeonHubConnection.getInstance()
                                            .getCarryTierFromCategory(server.getId(), category))
                            );

                    if (carryTier.isPresent()) {
                        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                                DungeonHubConnection.getInstance()
                                        .loadCarryDifficulties(carryTier.get()).stream()
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