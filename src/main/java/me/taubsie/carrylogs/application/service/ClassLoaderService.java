package me.taubsie.carrylogs.application.service;

import com.google.common.reflect.ClassPath;
import lombok.Getter;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.listener.Listener;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.listener.GloballyAttachableListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class ClassLoaderService {
    private static ClassLoaderService instance;

    @Getter
    private final Map<SlashCommandBuilder, Command> commandMap = new HashMap<>();
    @Getter
    private final List<GloballyAttachableListener> listeners = new ArrayList<>();

    private ClassLoaderService() {
        for(Class<?> clazz : getClassesInPackage(readPackage(getClass()))) {
            if(clazz.isAnnotationPresent(CommandParameters.class)) {
                Optional<CommandParameters> optionalCommandParameters = getCommandParameters(clazz);

                optionalCommandParameters.ifPresent(commandParameters ->
                {
                    try {
                        Command command = (Command) clazz.getDeclaredConstructor().newInstance();

                        SlashCommandBuilder slashCommandBuilder = buildSlashCommand(command, commandParameters);

                        commandMap.put(slashCommandBuilder, command);
                    }
                    catch(InstantiationException | IllegalAccessException | InvocationTargetException |
                          NoSuchMethodException | ClassCastException exception) {
                        exception.printStackTrace();
                    }
                });
            }

            if(clazz.isAnnotationPresent(Listener.class)
                    && clazz.isAssignableFrom(GloballyAttachableListener.class)) {
                try {
                    if(clazz.getDeclaredConstructor().newInstance() instanceof GloballyAttachableListener listener) {
                        listeners.add(listener);
                    }
                }
                catch(InstantiationException | IllegalAccessException | InvocationTargetException |
                      NoSuchMethodException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public static ClassLoaderService getInstance() {
        if(instance == null) {
            instance = new ClassLoaderService();
        }

        return instance;
    }

    /**
     * @return the first two entries seperated with a dot in the package name
     */
    public @NotNull String readPackage(@NotNull Class<?> clazz) {
        String fullName = clazz.getPackageName();
        String packageNameAfterFirstDot = fullName.substring(fullName.indexOf('.') + 1); //this is the string after
        // the first dot
        return fullName.substring(0, fullName.indexOf('.') + 1) + packageNameAfterFirstDot.substring(0,
                packageNameAfterFirstDot.indexOf('.'));
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

    @SuppressWarnings("UnstableApiUsage")
    private Set<Class<?>> getClassesInPackage(String packageName) {
        Set<Class<?>> classes = new HashSet<>();

        try {
            return ClassPath.from(ClassLoader.getSystemClassLoader())
                    .getAllClasses()
                    .stream()
                    .filter(classInfo -> classInfo.getPackageName().startsWith(packageName))
                    .map(ClassPath.ClassInfo::load)
                    .collect(Collectors.toSet());
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return classes;
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