/*
 * KissenEssentials
 * Copyright (C) KissenEssentials team and contributors.
 *
 * This program is free software and is free to redistribute
 * and/or modify under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is intended for the purpose of joy,
 * WITHOUT WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.taubsie.carrylogs;

import me.taubsie.carrylogs.enums.IdList;
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
public class AutoCompleteListener implements AutocompleteCreateListener
{
    @Override public void onAutocompleteCreate(AutocompleteCreateEvent autocompleteCreateEvent)
    {
        if (autocompleteCreateEvent.getAutocompleteInteraction().getChannel().isEmpty()
                || autocompleteCreateEvent.getAutocompleteInteraction().getChannel().get().asCategorizable().isEmpty()
                || autocompleteCreateEvent.getAutocompleteInteraction().getChannel().get().asCategorizable().get().getCategory().isEmpty()
                || autocompleteCreateEvent.getAutocompleteInteraction().getChannel().get().asCategorizable().get().getCategory().get().getServer().getId() != IdList.TEST_SERVER.getID())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
            return;
        }
        ChannelCategory category = autocompleteCreateEvent
                .getAutocompleteInteraction()
                .getChannel().get()
                .asCategorizable().get()
                .getCategory().get();

        Optional<IdList> idList = Arrays.stream(IdList.values()).filter(id -> id.getCARRY_TYPE() != null && id.getID() == category.getId()).findFirst();

        if (idList.isEmpty())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
            return;
        }

        autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(idList.get().getCARRY_TYPE().getChoiceList()).join();
    }
}