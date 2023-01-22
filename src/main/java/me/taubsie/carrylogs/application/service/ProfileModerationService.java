package me.taubsie.carrylogs.application.service;

import java.text.Normalizer;

public class ProfileModerationService
{
    private static ProfileModerationService instance;

    public static ProfileModerationService getInstance()
    {
        if (instance == null)
        {
            instance = new ProfileModerationService();
        }

        return instance;
    }

    public void checkUserName(String userName)
    {
        String checkAgainst = "Captcha.bot";

        System.out.println(userName + " > " + userName.equals(checkAgainst));
        String norm = Normalizer.normalize(userName, Normalizer.Form.NFKC).replaceAll("[^a-zA-Z]", "");
        System.out.println(norm + " > " + norm.equals(checkAgainst));
        System.out.println((int) norm.charAt(1));
    }
}