package me.taubsie.dungeonhub.application.loader;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import org.javacord.api.entity.server.Server;
import org.javacord.api.interaction.SlashCommand;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ClassLoaderService {
    private static ClassLoaderService instance;
    private final Map<SlashCommand, Command> slashCommandMap = new HashMap<>();

    public static ClassLoaderService getInstance() {
        if (instance == null) {
            instance = new ClassLoaderService();
        }

        return instance;
    }

    public Optional<Map.Entry<SlashCommand, Command>> getCommandData(String commandName, @Nullable Server server) {
        return slashCommandMap
                .entrySet()
                .parallelStream()
                .filter(entry -> entry.getKey().getName().equalsIgnoreCase(commandName))
                .filter(entry -> (entry.getKey().isGlobalApplicationCommand())
                        || (entry.getKey().isServerApplicationCommand()
                        && server != null
                        && entry.getKey().getServerId().isPresent()
                        && entry.getKey().getServerId().get() == server.getId()))
                .findAny();
    }

    public Optional<Command> getCommand(String commandName, @Nullable Server server) {
        return getCommandData(commandName, server).map(Map.Entry::getValue);
    }

    public Optional<SlashCommand> getSlashCommand(String commandName, @Nullable Server server) {
        return getCommandData(commandName, server).map(Map.Entry::getKey);
    }

    public Optional<CommandParameters> getCommandParameters(Class<?> clazz) {
        CommandParameters commandParameters = clazz.getAnnotation(CommandParameters.class);

        return (commandParameters != null
                && Command.class.isAssignableFrom(clazz)
                && !Modifier.isAbstract(clazz.getModifiers())
                && !Modifier.isInterface(clazz.getModifiers()))
                ? Optional.of(commandParameters)
                : Optional.empty();
    }
}