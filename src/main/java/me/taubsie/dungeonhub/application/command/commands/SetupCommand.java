package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.StringJoiner;

/**
 * Represents a command to execute the "setup" action, providing information on how to set up the bot.
 *
 * <p>The {@code SetupCommand} class extends the {@link Command} class and is annotated with {@link CommandParameters}
 * to provide information about the command, such as its name, description, and whether it is enabled in direct messages.
 * It overrides the {@link Command#executeCommand(SlashCommandCreateEvent)} method to handle the execution of the setup command.</p>
 *
 * <p>This command currently provides a basic response with information on setting up the bot. The implementation is marked as
 * unfinished with the message "Setup the bot! (command is unfinished)".</p>
 *
 * @see Command
 * @see CommandParameters
 */
@CommandParameters(name = "setup", description = "Shows you how to setup the bot.", enabledInDms = true)
public class SetupCommand extends Command {
    @Override
    protected void executeCommand(@NotNull SlashCommandCreateEvent slashCommandCreateEvent) {
        ApplicationService service = ApplicationService.getInstance();
        EmbedBuilder response = service.getEmbed().setColor(Color.YELLOW).setDescription(getDescription());
        respondEphemeral(response);
    }

    /**
     * Retrieves the description for the "setup" command.
     *
     * <p>The {@code getDescription} method constructs and returns the description for the "setup" command.
     * Currently, it provides a basic message indicating that the setup is unfinished.</p>
     *
     * @return the description for the "setup" command
     */
    @Contract(pure = true, value = "-> new")
    private static @NotNull String getDescription() {
        StringJoiner description = new StringJoiner(System.lineSeparator());
        description.add("> Setup the bot!");
        description.add("> (command is unfinished)");
        return description.toString();
    }
}