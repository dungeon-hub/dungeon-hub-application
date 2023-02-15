package me.taubsie.carrylogs.application.service;

import lombok.Getter;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

public class ClassLoaderService
{
    private static ClassLoaderService instance;

    @Getter
    private final Map<SlashCommandBuilder, Command> commandMap;

    public static ClassLoaderService getInstance()
    {
        if (instance == null)
        {
            instance = new ClassLoaderService();
        }

        return instance;
    }

    /**
     * @return the first two entries seperated with a dot in the package name
     */
    public @NotNull String readPackage(@NotNull Class<?> clazz)
    {
        String fullName = clazz.getPackageName();
        String packageNameAfterFirstDot = fullName.substring(fullName.indexOf('.') + 1); //this is the string after the first dot
        return fullName.substring(0, fullName.indexOf('.') + 1) + packageNameAfterFirstDot.substring(0, packageNameAfterFirstDot.indexOf('.'));
    }

    private SlashCommandBuilder buildSlashCommand(Command command, CommandParameters commandParameters)
    {
        SlashCommandBuilder slashCommandBuilder = new SlashCommandBuilder()
                .setName(commandParameters.name())
                .setDescription(commandParameters.description())
                .setOptions(command.getSlashCommandOptions())
                .setEnabledInDms(commandParameters.enabledInDms());

        if (commandParameters.enabledForPermissions().length != 0)
        {
            slashCommandBuilder.setDefaultEnabledForPermissions(commandParameters.enabledForPermissions());
        }

        return slashCommandBuilder;
    }

    private ClassLoaderService()
    {
        commandMap = new HashMap<>();

        for (Class<?> clazz : getClassesInPackage(readPackage(getClass())))
        {
            Optional<CommandParameters> optionalCommandParameters = getCommandParameters(clazz);

            optionalCommandParameters.ifPresent(commandParameters ->
            {
                try
                {
                    Command command = (Command) clazz.getDeclaredConstructor().newInstance();

                    SlashCommandBuilder slashCommandBuilder = buildSlashCommand(command, commandParameters);

                    commandMap.put(slashCommandBuilder, command);
                }
                catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                       NoSuchMethodException | ClassCastException exception)
                {
                    exception.printStackTrace();
                }
            });
        }
    }

    private Set<Class<?>> getClassesInPackage(String packageName)
    {
        Set<Class<?>> classes = new HashSet<>();

        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(packageName.replaceAll("[.]", "/")))
        {
            if (inputStream == null)
            {
                return classes;
            }

            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream)))
            {
                bufferedReader.lines().forEach(fileName ->
                {
                    if (fileName.endsWith(".class"))
                    {
                        getClass(fileName, packageName).ifPresent(classes::add);
                        return;
                    }

                    if (!fileName.contains("."))
                    {
                        classes.addAll(getClassesInPackage(packageName + "." + fileName));
                    }
                });
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return classes;
    }

    private Optional<Class<?>> getClass(String className, String packageName)
    {
        try
        {
            return Optional.of(Class.forName(packageName + "." + className.substring(0, className.lastIndexOf("."))));
        }
        catch (ClassNotFoundException classNotFoundException)
        {
            classNotFoundException.printStackTrace();
            return Optional.empty();
        }
    }

    public Optional<CommandParameters> getCommandParameters(Class<?> clazz)
    {
        CommandParameters commandParameters = clazz.getAnnotation(CommandParameters.class);

        return (commandParameters != null
                && Command.class.isAssignableFrom(clazz)
                && !Modifier.isAbstract(clazz.getModifiers())
                && !Modifier.isInterface(clazz.getModifiers()))
                ? Optional.of(commandParameters)
                : Optional.empty();
    }
}