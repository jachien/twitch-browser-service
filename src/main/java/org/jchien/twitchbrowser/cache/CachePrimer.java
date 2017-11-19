package org.jchien.twitchbrowser.cache;

import org.jchien.twitchbrowser.PopularGamesRequest;
import org.jchien.twitchbrowser.PopularGamesResponse;
import org.jchien.twitchbrowser.StreamsRequest;
import org.jchien.twitchbrowser.StreamsResponse;
import org.jchien.twitchbrowser.twitch.CachingTwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchGame;
import org.jchien.twitchbrowser.util.LoggingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author jchien
 */
@Component
public class CachePrimer {
    private static final Logger LOG = LoggerFactory.getLogger(CachePrimer.class);

    private static final int GAME_LIMIT = 50; // num games to try priming

    private static final long EXPIRING_SOON_MILLIS = TimeUnit.SECONDS.toMillis(30); // naming is hard

    private static final long DELAY_MILLIS = 5000; // time between batched prime runs

    private TwitchApiService service;

    private static final int NUM_THREADS = 8;

    private ExecutorService executors = Executors.newFixedThreadPool(NUM_THREADS, new LoggingThreadFactory("CachePrimerThread"));

    @Autowired
    public CachePrimer(@Qualifier("cachingTwitchApiService") TwitchApiService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = DELAY_MILLIS)
    private void primeCache() {
        PopularGamesRequest request = PopularGamesRequest.newBuilder()
                .setLimit(GAME_LIMIT)
                .build();
        try {
            PopularGamesResponse response = service.getPopularGames(request);
            List<TwitchGame> games = response.getGamesList();
            List<Future<PrimeResult>> results = new ArrayList<>(GAME_LIMIT);

            for (TwitchGame game : games) {
                Future<PrimeResult> future = executors.submit(new PrimeTask(game.getGameName()));
                results.add(future);
            }

            for (Future<PrimeResult> future : results) {
                try {
                    // todo track stats based on result
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOG.warn("problem priming cache for single game", e);
                }
            }
        } catch (IOException e) {
            LOG.warn("failed to get popular games", e);
        }
    }

    private enum PrimeResult {
        HIT,
        EXPIRING_SOON,
        MISS,
        FAIL,
    }

    private class PrimeTask implements Callable<PrimeResult> {
        private final String gameName;

        public PrimeTask(String gameName) {
            this.gameName = gameName;
        }

        @Override
        public PrimeResult call() throws Exception {
            try {
                StreamsResponse response = service.getStreams(buildRequest(false));
                if (!response.getFromCache()) {
                    // we got fresh results so it wasn't in cache or was too old
                    return PrimeResult.MISS;
                }

                long responseTimestamp = response.getTimestamp();
                if (isExpiringSoon(responseTimestamp)) {
                    service.getStreams(buildRequest(true));
                    // assume no exception thrown means successful call
                    return PrimeResult.EXPIRING_SOON;
                }

                // clean cache hit
                return PrimeResult.HIT;

            } catch (IOException e) {
                LOG.warn("failed to prime cache for " + gameName, e);
                return PrimeResult.FAIL;
            }
        }

        private StreamsRequest buildRequest(boolean disallowCache) {
            return StreamsRequest.newBuilder()
                    .setGameName(gameName)
                    .setStart(0)
                    .setLimit(CachingTwitchApiService.GAME_STREAM_LIMIT)
                    .setDisallowCache(disallowCache)
                    .build();
        }

        private boolean isExpiringSoon(long responseTimestamp) {
            long now = System.currentTimeMillis();
            long staleTime = responseTimestamp + CachingTwitchApiService.STALE_MS;
            long expiringSoonTime = staleTime - EXPIRING_SOON_MILLIS;
            return now >= expiringSoonTime;
        }
    }
}

