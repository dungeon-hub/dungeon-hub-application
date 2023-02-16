package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.enums.IdList;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.event.interaction.AutocompleteCreateEvent;
import org.javacord.api.listener.interaction.AutocompleteCreateListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class AutoCompleteListener implements AutocompleteCreateListener
{
    @Override
    public void onAutocompleteCreate(AutocompleteCreateEvent autocompleteCreateEvent)
    {
        if (autocompleteCreateEvent.getAutocompleteInteraction().getChannel().isEmpty()
                || autocompleteCreateEvent.getAutocompleteInteraction().getChannel().get().asCategorizable().isEmpty()
                || autocompleteCreateEvent.getAutocompleteInteraction().getChannel().get().asCategorizable().get().getCategory().isEmpty()
                || autocompleteCreateEvent.getAutocompleteInteraction().getServer().isEmpty()
                || autocompleteCreateEvent.getAutocompleteInteraction().getServer().get().getId() != IdList.SERVER.getLocalId(autocompleteCreateEvent.getAutocompleteInteraction().getServer().get().getId()))
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
            return;
        }
        ChannelCategory category = autocompleteCreateEvent
                .getAutocompleteInteraction()
                .getChannel().get()
                .asCategorizable().get()
                .getCategory().get();

        Optional<IdList> idList = Arrays.stream(IdList.values()).filter(id -> id.getCarryType() != null && id.getLocalId(autocompleteCreateEvent.getAutocompleteInteraction().getServer().get().getId()) == category.getId()).findFirst();

        if (idList.isEmpty())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
            return;
        }

        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(idList.get().getCarryType().getChoiceList()).join();
    }
}