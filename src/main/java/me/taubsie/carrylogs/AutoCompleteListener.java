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

import me.taubsie.carrylogs.enums.CarryType;
import me.taubsie.carrylogs.enums.IdList;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.event.interaction.AutocompleteCreateEvent;
import org.javacord.api.listener.interaction.AutocompleteCreateListener;

import java.util.ArrayList;

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
                || autocompleteCreateEvent.getAutocompleteInteraction().getChannel().get().asCategorizable().get().getCategory().get().getServer().getId() != IdList.TEST_SERVER.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
            return;
        }
        ChannelCategory category = autocompleteCreateEvent
                .getAutocompleteInteraction()
                .getChannel().get()
                .asCategorizable().get()
                .getCategory().get();

        if (category.getId() == IdList.TEST_F4_CATEGORY.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(CarryType.F4.getChoiceList());
        }
        else if (category.getId() == IdList.TEST_F5_CATEGORY.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(CarryType.F5.getChoiceList());
        }
        else if (category.getId() == IdList.TEST_F6_CATEGORY.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(CarryType.F6.getChoiceList());
        }
        else if (category.getId() == IdList.TEST_F7_CATEGORY.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(CarryType.F7.getChoiceList());
        }
        else if (category.getId() == IdList.TEST_MASTER_CATEGORY.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(CarryType.MASTER_MODE.getChoiceList());
        }
        else if (category.getId() == IdList.TEST_EMAN_CATEGORY.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(CarryType.EMAN.getChoiceList());
        }
        else if (category.getId() == IdList.TEST_BLAZE_CATEGORY.getId())
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(CarryType.BLAZE.getChoiceList());
        }
        else
        {
            autocompleteCreateEvent.getAutocompleteInteraction().respondWithChoices(new ArrayList<>()).join();
        }
    }
}