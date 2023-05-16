package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.enums.CarryType;
import me.taubsie.carrylogs.application.enums.IdList;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.AutocompleteCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.listener.interaction.AutocompleteCreateListener;

import java.util.*;
import java.util.stream.Stream;

//TODO test edited code
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

            if(server.getId() != IdList.SERVER.getLocalId(server.getId())) {
                throw new NoSuchElementException();
            }

            Stream<CarryType> carryType = Stream.empty();

            if(autocompleteCreateEvent.getAutocompleteInteraction().getCommandName().equalsIgnoreCase("calc-price")) {
                Optional<SlashCommandInteractionOption> typeOption =
                        autocompleteCreateEvent.getAutocompleteInteraction().getOptionByName("type");

                if(typeOption.isPresent()) {
                    carryType = Stream.ofNullable(getCarryTypeFromOption(typeOption.get()));
                }
            }

            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(
                    Stream.concat(
                                    Arrays.stream(IdList.values())
                                            .filter(id -> id.getCarryType() != null
                                                    && (id.getLocalId(server.getId()) == autocompleteCreateEvent.getAutocompleteInteraction().getChannel().orElseThrow().asCategorizable().orElseThrow().getCategory().orElseThrow().getId()))
                                            .map(IdList::getCarryType),
                                    carryType
                            )
                            .map(CarryType::getChoiceList)
                            .flatMap(Collection::stream)
                            .distinct()
                            .toList()
            ).join();
        }
        catch(NoSuchElementException noSuchElementException) {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
        }
    }

    private CarryType getCarryTypeFromOption(SlashCommandInteractionOption typeOption) {
        try {
            return CarryType.valueOf(typeOption.getStringValue().orElse(""));
        }
        catch(IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }
}