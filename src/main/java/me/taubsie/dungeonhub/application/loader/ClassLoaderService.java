package me.taubsie.dungeonhub.application.loader;

import com.google.common.reflect.ClassPath;
import com.google.errorprone.annotations.DoNotCall;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.listener.Listener;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.server.Server;
import org.javacord.api.interaction.ApplicationCommand;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.listener.GloballyAttachableListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class ClassLoaderService {
    private static final Map<StartupListener, OnStart> startupListeners = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ClassLoaderService.class);
    private static ClassLoaderService instance;
    private final Map<SlashCommandBuilder, Command> commandMap = new HashMap<>();
    private final Map<SlashCommand, Command> slashCommandMap = new HashMap<>();

    private ClassLoaderService() {
        try {
            for(Map.Entry<Class<Command>, CommandParameters> commandEntry :
                    getClassesInPackage(readPackage(getClass()),
                            Command.class,
                            CommandParameters.class).entrySet()) {
                Command command = commandEntry.getKey().getDeclaredConstructor().newInstance();

                SlashCommandBuilder slashCommandBuilder = buildSlashCommand(command, commandEntry.getValue());

                commandMap.put(slashCommandBuilder, command);
            }
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException |
               NoSuchMethodException | ClassCastException exception) {
            logger.error(null, exception);
        }
    }

    public static ClassLoaderService getInstance() {
        if (instance == null) {
            instance = new ClassLoaderService();
        }

        return instance;
    }

    public <T extends StartupListener> void addStartupListener(T listener, OnStart onStart) {
        startupListeners.put(listener, onStart);
    }

    public void loadStartupListeners() {
        for(Map.Entry<Class<StartupListener>, OnStart> entry : getClassesInPackage(readPackage(getClass()),
                StartupListener.class,
                OnStart.class).entrySet()) {
            try {
                addStartupListener(getListenerInstance(entry.getKey()), entry.getValue());
            }
            catch (InvocationTargetException | InstantiationException | IllegalAccessException |
                   NoSuchMethodException exception) {
                logger.error("Couldn't get instance of given listener \"" + entry.getKey().toString() + "\", " +
                        "causing it to not be executed on startup.", exception);
            }
        }
    }

    private StartupListener getListenerInstance(Class<StartupListener> clazz) throws NoSuchMethodException,
            InvocationTargetException,
            IllegalAccessException, InstantiationException {
        try {
            if (Modifier.isStatic(clazz.getDeclaredMethod("getInstance").getModifiers())
                    && clazz.getDeclaredMethod("getInstance").invoke(null) instanceof StartupListener currentInstance) {
                return currentInstance;
            } else {
                throw new NoSuchMethodException();
            }
        }
        catch (NoSuchMethodException noSuchMethodException) {
            return clazz.getDeclaredConstructor().newInstance();
        }
    }

    public List<StartupListener> getSortedListeners() {
        return startupListeners
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(value -> value.getValue().priority()))
                .map(Map.Entry::getKey)
                .toList();
    }

    public void executeStartup() {
        for(StartupListener startupListener : getSortedListeners()) {
            startupListener.preStart();
        }

        for(StartupListener startupListener : getSortedListeners()) {
            startupListener.onStart();
        }

        for(StartupListener startupListener : getSortedListeners()) {
            startupListener.postStart();
        }
    }

    /**
     * @return the first two entries seperated with a dot in the package name
     */
    public @NotNull String readPackage(@NotNull Class<?> clazz) {
        String fullName = clazz.getPackageName();
        String packageNameAfterFirstDot = fullName.substring(fullName.indexOf('.') + 1);
        return fullName.substring(0, fullName.indexOf('.') + 1) + packageNameAfterFirstDot.substring(0,
                packageNameAfterFirstDot.indexOf('.'));
    }

    public Set<Class<?>> getClassesInPackage(String packageName) {
        Set<Class<?>> classes = new HashSet<>();

        try {
            classes.addAll(
                    ClassPath.from(ClassLoader.getSystemClassLoader())
                            .getAllClasses()
                            .stream()
                            .filter(classInfo -> classInfo.getPackageName().startsWith(packageName))
                            .map(ClassPath.ClassInfo::load)
                            .collect(Collectors.toSet())
            );
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }

        return classes;
    }

    @DoNotCall("possibly unsafe")
    public <T, A extends Annotation> Map<Class<T>, A>
    getClassesInPackage(String packageName, Class<T> clazz, Class<A> annotation) {
        Map<Class<T>, A> classes = new HashMap<>();

        try {
            //noinspection unchecked
            ClassPath.from(ClassLoader.getSystemClassLoader())
                    .getAllClasses()
                    .stream()
                    .filter(classInfo -> classInfo.getPackageName().startsWith(packageName))
                    .map(ClassPath.ClassInfo::load)
                    .filter(cls -> cls.isAnnotationPresent(annotation)
                            && cls.getAnnotation(annotation) != null
                            && clazz.isAssignableFrom(cls))
                    .filter(cls -> !Modifier.isAbstract(cls.getModifiers())
                            && !Modifier.isInterface(cls.getModifiers()))
                    // might want to look into if this can be done without unsafe casts -> unchecked inspection
                    // had no issues with it yet, so it probably is fine, but the noinspection isn't perfect
                    .forEach(cls -> classes.put((Class<T>) cls, cls.getAnnotation(annotation)));
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }

        return classes;
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

    private SlashCommandBuilder buildSlashCommand(Command command, CommandParameters commandParameters) {
        SlashCommandBuilder slashCommandBuilder = new SlashCommandBuilder()
                .setName(commandParameters.name())
                .setDescription(commandParameters.description())
                .setEnabledInDms(commandParameters.enabledInDms());

        if (commandParameters.enabledForPermissions().length != 0) {
            slashCommandBuilder.setDefaultEnabledForPermissions(commandParameters.enabledForPermissions());
        }

        if (!command.getSlashCommandOptions().isEmpty()) {
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
            }
            catch (InvocationTargetException | InstantiationException | IllegalAccessException |
                   NoSuchMethodException exception) {
                logger.error(null, exception);
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

                if (server.isPresent()) {
                    Set<ApplicationCommand> serverCommands = bot.bulkOverwriteServerApplicationCommands(server.get(),
                            entry.getValue()).join();
                    for(ApplicationCommand applicationCommand : serverCommands) {
                        if (applicationCommand instanceof SlashCommand slashCommand) {
                            Command command =
                                    commandMap.values().stream().filter(command1 -> command1.getCommandName().equalsIgnoreCase(applicationCommand.getName())).findFirst().orElse(null);
                            slashCommandMap.put(slashCommand, command);
                        }
                    }
                }
            }
            catch (CompletionException completionException) {
                logger.error(null, completionException);
            }
        }
    }

    public void loadGlobalSlashCommands(DiscordApi bot) {
        Set<ApplicationCommand> globalCommands =
                bot.bulkOverwriteGlobalApplicationCommands(commandMap.entrySet().stream()
                        .filter(entry -> entry.getValue().isGlobal())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet())).join();

        for(ApplicationCommand applicationCommand : globalCommands) {
            if (applicationCommand instanceof SlashCommand slashCommand) {
                Command command =
                        commandMap.values().stream().filter(command1 -> command1.getCommandName().equalsIgnoreCase(applicationCommand.getName())).findFirst().orElse(null);
                slashCommandMap.put(slashCommand, command);
            }
        }
    }
}