package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.enums.IdList;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.AutocompleteCreateEvent;
import org.javacord.api.listener.interaction.AutocompleteCreateListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;

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
                autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
                return;
            }

            Optional<IdList> idList =
                    Arrays.stream(IdList.values())
                            .filter(id -> id.getCarryType() != null
                                    && (id.getLocalId(server.getId()) == autocompleteCreateEvent.getAutocompleteInteraction().getChannel().orElseThrow().asCategorizable().orElseThrow().getCategory().orElseThrow().getId()))
                            .findFirst();

            if(idList.isEmpty()) {
                autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
                return;
            }

            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(idList.get().getCarryType().getChoiceList()).join();
        }
        catch(NoSuchElementException noSuchElementException) {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
        }
    }
}