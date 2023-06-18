package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.CarryType;
import me.taubsie.carrylogs.application.enums.IdList;
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

            if (server.getId() != IdList.SERVER.getLocalId(server.getId())) {
                throw new NoSuchElementException();
            }

            if (autocompleteCreateEvent.getAutocompleteInteraction().getFocusedOption().getName().equalsIgnoreCase("carry-type")) {
                autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                        DungeonHubConnection.getInstance()
                                .loadCarryTypesForServer(server.getId())
                                .stream()
                                .map(carryType -> SlashCommandOptionChoice.create(carryType.getDisplayName(), carryType.getIdentifier()))
                                .toList()
                );
                return;
            }

            if (autocompleteCreateEvent.getAutocompleteInteraction().getFocusedOption().getName().equalsIgnoreCase("carry-tier")) {
                if (autocompleteCreateEvent.getAutocompleteInteraction().getOptionByName("carry-type").isPresent()) {
                    Optional<String> carryTypeIdentifier = autocompleteCreateEvent.getAutocompleteInteraction().getOptionByName("carry-type").get().getStringValue();

                    if (carryTypeIdentifier.isPresent()) {
                        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                                DungeonHubConnection.getInstance()
                                        .loadCarryType(server.getId(), carryTypeIdentifier.get())
                                        .map(carryType -> DungeonHubConnection.getInstance().loadCarryTiers(carryType))
                                        .orElse(List.of())
                                        .stream()
                                        .map(carryTier -> SlashCommandOptionChoice.create(carryTier.getDisplayName(), carryTier.getIdentifier()))
                                        .toList()
                        );
                        return;
                    }
                }
            }

            CarryType carryType = null;

            if (autocompleteCreateEvent.getAutocompleteInteraction().getCommandName().equalsIgnoreCase("calc-price")) {
                Optional<SlashCommandInteractionOption> typeOption =
                        autocompleteCreateEvent.getAutocompleteInteraction().getOptionByName("type");

                if (typeOption.isPresent()) {
                    carryType = getCarryTypeFromOption(typeOption.get());
                }
            }

            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                    ((carryType != null)
                            ? Stream.ofNullable(carryType)
                            : Arrays.stream(IdList.values())
                            .filter(id -> id.getCarryType() != null
                                    && (id.getLocalId(server.getId()) == autocompleteCreateEvent.getAutocompleteInteraction()
                                    .getChannel().orElseThrow()
                                    .asCategorizable().orElseThrow()
                                    .getCategory().orElseThrow()
                                    .getId()))
                            .map(IdList::getCarryType))
                            .map(CarryType::getChoiceList)
                            .flatMap(Collection::stream)
                            .distinct()
                            .toList()
            ).join();
        }
        catch (NoSuchElementException noSuchElementException) {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
        }
    }

    private CarryType getCarryTypeFromOption(SlashCommandInteractionOption typeOption) {
        try {
            return CarryType.valueOf(typeOption.getStringValue().orElse(""));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }
}