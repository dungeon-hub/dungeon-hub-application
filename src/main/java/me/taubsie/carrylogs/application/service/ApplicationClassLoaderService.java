package me.taubsie.carrylogs.application.service;

import me.taubsie.dungeonhub.common.ClassLoaderService;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.listener.Listener;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.server.Server;
import org.javacord.api.interaction.ApplicationCommand;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.listener.GloballyAttachableListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class ApplicationClassLoaderService extends ClassLoaderService {
    private static ApplicationClassLoaderService instance;
    private final Map<SlashCommandBuilder, Command> commandMap = new HashMap<>();
    private final Map<SlashCommand, Command> slashCommandMap = new HashMap<>();

    private ApplicationClassLoaderService() {
        try {
            for(Map.Entry<Class<Command>, CommandParameters> commandEntry :
                    getClassesInPackage(readPackage(getClass()),
                            Command.class,
                            CommandParameters.class).entrySet()) {
                Command command = commandEntry.getKey().getDeclaredConstructor().newInstance();

                SlashCommandBuilder slashCommandBuilder = buildSlashCommand(command, commandEntry.getValue());

                commandMap.put(slashCommandBuilder, command);
            }
        } catch(InstantiationException | IllegalAccessException | InvocationTargetException |
                NoSuchMethodException | ClassCastException exception) {
            exception.printStackTrace();
        }
    }

    public static ApplicationClassLoaderService getInstance() {
        if(instance == null) {
            instance = new ApplicationClassLoaderService();
        }

        return instance;
    }

    public Optional<Map.Entry<SlashCommand, Command>> getCommandData(String commandName, Server server) {
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

    public Optional<Command> getCommand(String commandName, Server server) {
        return getCommandData(commandName, server).map(Map.Entry::getValue);
    }

    public Optional<SlashCommand> getSlashCommand(String commandName, Server server) {
        return getCommandData(commandName, server).map(Map.Entry::getKey);
    }

    private SlashCommandBuilder buildSlashCommand(Command command, CommandParameters commandParameters) {
        SlashCommandBuilder slashCommandBuilder = new SlashCommandBuilder()
                .setName(commandParameters.name())
                .setDescription(commandParameters.description())
                .setEnabledInDms(commandParameters.enabledInDms());

        if(commandParameters.enabledForPermissions().length != 0) {
            slashCommandBuilder.setDefaultEnabledForPermissions(commandParameters.enabledForPermissions());
        }

        if(!command.getSlashCommandOptions().isEmpty()) {
            slashCommandBuilder.setOptions(command.getSlashCommandOptions());
        }

        return slashCommandBuilder;
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

    public void loadListeners(DiscordApi bot) {
        for(Class<GloballyAttachableListener> listenerClass :
                getClassesInPackage(readPackage(getClass()),
                        GloballyAttachableListener.class,
                        Listener.class).keySet()) {
            try {
                bot.addListener(listenerClass.getDeclaredConstructor().newInstance());
            } catch(InvocationTargetException | InstantiationException | IllegalAccessException |
                    NoSuchMethodException exception) {
                exception.printStackTrace();
            }
        }
    }

    public void loadServerSlashCommands(DiscordApi bot) {
        Map<Long, Set<SlashCommandBuilder>> serverCommandBuilders = new HashMap<>();

        commandMap.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isGlobal())
                .forEach(entry ->
                        {
                            long[] serverIds = entry.getValue().getEnabledServers();
                            for(long id : serverIds) {
                                Set<SlashCommandBuilder> resultSet = serverCommandBuilders.containsKey(id)
                                        ? serverCommandBuilders.get(id)
                                        : new HashSet<>();

                                resultSet.add(entry.getKey());
                                serverCommandBuilders.put(id, resultSet);
                            }
                        }
                );

        for(Map.Entry<Long, Set<SlashCommandBuilder>> entry : serverCommandBuilders.entrySet()) {
            try {
                Optional<Server> server = bot.getServerById(entry.getKey());

                if(server.isPresent()) {
                    Set<ApplicationCommand> serverCommands = bot.bulkOverwriteServerApplicationCommands(server.get(),
                            entry.getValue()).join();
                    for(ApplicationCommand applicationCommand : serverCommands) {
                        if(applicationCommand instanceof SlashCommand slashCommand) {
                            Command command =
                                    commandMap.values().stream().filter(command1 -> command1.getCommandName().equalsIgnoreCase(applicationCommand.getName())).findFirst().orElse(null);
                            slashCommandMap.put(slashCommand, command);
                        }
                    }
                }
            } catch(CompletionException completionException) {
                completionException.printStackTrace();
            }
        }
    }

    public void loadGlobalSlashCommands(DiscordApi bot) {
        Set<ApplicationCommand> globalCommands =
                bot.bulkOverwriteGlobalApplicationCommands(commandMap.entrySet().stream().filter(entry -> entry.getValue().isGlobal()).map(Map.Entry::getKey).collect(Collectors.toSet())).join();

        for(ApplicationCommand applicationCommand : globalCommands) {
            if(applicationCommand instanceof SlashCommand slashCommand) {
                Command command =
                        commandMap.values().stream().filter(command1 -> command1.getCommandName().equalsIgnoreCase(applicationCommand.getName())).findFirst().orElse(null);
                slashCommandMap.put(slashCommand, command);
            }
        }
    }
}