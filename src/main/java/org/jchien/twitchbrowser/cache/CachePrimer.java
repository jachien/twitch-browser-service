package org.jchien.twitchbrowser.cache;

import org.jchien.twitchbrowser.PopularGamesRequest;
import org.jchien.twitchbrowser.PopularGamesResponse;
import org.jchien.twitchbrowser.StreamsRequest;
import org.jchien.twitchbrowser.twitch.CachingTwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchGame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * @author jchien
 */
@Component
public class CachePrimer {
    private static final Logger LOG = LoggerFactory.getLogger(CachePrimer.class);

    private static final long DELAY_MILLIS = 5000;

    private TwitchApiService service;

    @Autowired
    public CachePrimer(@Qualifier("cachingTwitchApiService") TwitchApiService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = DELAY_MILLIS)
    private void primeCache() {
        PopularGamesRequest request = PopularGamesRequest.newBuilder()
                .setLimit(50)
                .build();
        try {
            PopularGamesResponse response = service.getPopularGames(request);
            List<TwitchGame> games = response.getGamesList();
            for (TwitchGame game : games) {
                primeCache(game.getGameName());
            }

        } catch (IOException e) {
            LOG.warn("failed to get popular games", e);
        }
    }

    private void primeCache(String gameName) {
        StreamsRequest streamsRequest = StreamsRequest.newBuilder()
                .setGameName(gameName)
                .setStart(0)
                .setLimit(CachingTwitchApiService.GAME_STREAM_LIMIT)
                .setDisallowCache(true) // force it to be fresh fetched
                .build();

        // todo refresh cache only if results are expired or going to expire soon

        try {
            service.getStreams(streamsRequest);
            // we don't care about the result
        } catch (IOException e) {
            LOG.warn("failed to prime cache for " + gameName, e);
        }
    }
}

