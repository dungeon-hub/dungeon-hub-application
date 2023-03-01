package me.taubsie.carrylogs.application.command;

import org.javacord.api.entity.permission.PermissionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandParameters
{
    String name();

    String description() default "No description specified.";

    boolean enabledInDms() default false;

    PermissionType[] enabledForPermissions() default {};

    long[] enabledForUsers() default {};
}