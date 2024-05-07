package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.ModalSubmitEvent;
import org.javacord.api.interaction.ModalInteraction;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;
import org.javacord.api.listener.interaction.ModalSubmitListener;

import java.util.Optional;
import java.util.UUID;

@Listener
public class ModalListener implements ModalSubmitListener {
    @Override
    public void onModalSubmit(ModalSubmitEvent modalSubmitEvent) {
        switch (modalSubmitEvent.getModalInteraction().getCustomId().toLowerCase()) {
            case "link_needed" -> linkNeeded(modalSubmitEvent.getModalInteraction());
            //TODO switch to linkIgn()
            case "link_ign" -> linkNeeded(modalSubmitEvent.getModalInteraction());
            default -> modalSubmitEvent.getModalInteraction()
                    .createImmediateResponder()
                    .setContent("Unknown interaction, this will be implemented later. Sorry!")
                    .setFlags(MessageFlag.EPHEMERAL)
                    .respond();
        }
    }

    public void linkIgn(ModalInteraction modalInteraction) {
        //TODO implement
    }

    public void linkNeeded(ModalInteraction modalInteraction) {
        User user = modalInteraction.getUser();
        Optional<String> ign = modalInteraction.getTextInputValueByCustomId("ign").filter(s -> !s.isBlank());

        InteractionOriginalResponseUpdater updater = modalInteraction.respondLater().join();

        try {
            if (ign.isEmpty()) {
                throw new InvalidOptionException("ign",
                        "Please enter a valid Ingame-Name.");
            }

            UUID linkedId = NicknameService.getInstance().linkToIgn(ign.get(), user);

            updater.addEmbed(ApplicationService.getInstance()
                            .getEmbed()
                            .setTitle("Linked successfully")
                            .setDescription("Your UUID is now `" + linkedId + "`")
                            .setColor(EmbedColor.POSITIVE.getColor()))
                    .setFlags(MessageFlag.EPHEMERAL)
                    .update();
        }
        catch (CommandExecutionException commandExecutionException) {
            updater.addEmbed(ApplicationService.getInstance()
                            .getErrorEmbed(commandExecutionException))
                    .setFlags(MessageFlag.EPHEMERAL)
                    .update();
        }

        RolesService.getInstance().updateRoles(user);
    }
}