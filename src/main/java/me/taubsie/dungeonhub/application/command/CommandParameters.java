package me.taubsie.dungeonhub.application.command;

import org.javacord.api.entity.permission.PermissionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation contains all important parameters that are needed to register a slash command on discord.
 * When a class extends {@link Command} and is annotated by this, it will automatically be picked up by the class
 * scanner and registered to be sent to discord on start.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandParameters {
    String name();

    String description() default "No description specified.";

    boolean enabledInDms() default false;

    PermissionType[] enabledForPermissions() default {};

    long[] enabledForUsers() default {};
}