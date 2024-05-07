package me.taubsie.dungeonhub.application.exceptions;

import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;

public class HypixelLinkedToOtherException extends CommandExecutionException {
    public HypixelLinkedToOtherException(String ign) {
        super("`" + ign + "` has their ingame discord-account set to something else.\n" +
                "If you need more information about linking your discord account, please use `/help verification`");
    }

    public HypixelLinkedToOtherException(String ign, String wrongUsername, String actualUsername) {
        super("`" + ign + "` has their ingame discord-account set to `" + wrongUsername + "`.\n" +
                "If that is your account, please change it to `" + actualUsername + "` and wait for it to update.\n" +
                "If you need more information about linking your discord account, please use `/help verification`");
    }
}