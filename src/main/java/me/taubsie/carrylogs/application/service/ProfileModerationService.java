package me.taubsie.carrylogs.application.service;

import net.codebox.homoglyph.Homoglyph;
import net.codebox.homoglyph.HomoglyphBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileModerationService
{
    private static ProfileModerationService instance;
    private final Homoglyph homoglyph;
    private static final String[] forbiddenUsernames = new String[]{
            "Captcha.bot",
            "Dyno",
            "Carl-bot",
            "Xenon",
            "SkyKings",
            "SkyHelper",
            "MEE6",
            "Dungeon Hub Bot"
    };

    public static ProfileModerationService getInstance()
    {
        if (instance == null)
        {
            instance = new ProfileModerationService();
        }

        return instance;
    }

    ProfileModerationService()
    {
        try
        {
            this.homoglyph = HomoglyphBuilder.build();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public String checkUserName(String userName)
    {
        List<Homoglyph.SearchResult> searchResults = homoglyph.search(userName, forbiddenUsernames);

        if (searchResults.isEmpty())
        {
            return null;
        }

        return searchResults.stream().map(searchResult -> searchResult.match).collect(Collectors.joining("; "));
    }

    public boolean isOverwritten(long userId)
    {
        return false;
    }
}