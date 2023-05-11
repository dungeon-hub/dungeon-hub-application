package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;

public class LeaderboardMessage extends PageableMessage {
    private final String type;

    public LeaderboardMessage(int currentPage, long channel, long message, String type) {
        super(currentPage, channel, message);

        this.type = type;
    }

    @Override
    public int getMaxPage() {
        return ConnectionService.getInstance().getMaxLeaderboardPage(type);
    }

    @Override
    public void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage) {
        updater.removeAllEmbeds()
                .addEmbed(
                        LeaderboardService.getInstance().getLeaderboardEmbed(
                                LeaderboardService.getInstance().getLeaderboardTitle(type),
                                ConnectionService.getInstance().getLeaderboardData(type, currentPage),
                                currentPage,
                                getMaxPage()
                        )
                ).update();
    }
}