package org.jchien.twitchbrowser.twitch;

import com.google.protobuf.InvalidProtocolBufferException;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.RedisException;
import com.lambdaworks.redis.RedisFuture;
import com.lambdaworks.redis.api.async.RedisAsyncCommands;
import org.jchien.twitchbrowser.PopularGamesRequest;
import org.jchien.twitchbrowser.PopularGamesResponse;
import org.jchien.twitchbrowser.StreamsRequest;
import org.jchien.twitchbrowser.StreamsResponse;
import org.jchien.twitchbrowser.cache.CacheClient;
import org.jchien.twitchbrowser.cache.CacheResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author jchien
 */
@Component("cachingTwitchApiService")
public class CachingTwitchApiService implements TwitchApiService {
    private static final Logger LOG = LoggerFactory.getLogger(CachingTwitchApiService.class);

    private static final String CACHE_NAMESPACE = "twitchbrowser.v2";

    // cache lookup timeout
    private static final long TIMEOUT_MS = 200;

    // do fresh query if cache result is this old
    private static final long STALE_MS = TimeUnit.MINUTES.toMillis(2);

    private static final int GAME_STREAM_LIMIT = 25;

    private CacheClient cacheClient;

    private final TwitchApiService wrappedService;

    @Autowired
    public CachingTwitchApiService(@Qualifier("basicTwitchApiService") TwitchApiService wrappedService,
                                   CacheClient cacheClient) {
        this.cacheClient = cacheClient;
        this.wrappedService = wrappedService;
    }

    public StreamsResponse getStreams(StreamsRequest request) throws IOException {
        final boolean isCacheable = isCacheableRequest(request);

        final long now = System.currentTimeMillis();
        final CacheResult cacheResult;
        if (isCacheable) {
            cacheResult = getCacheResult(request);
            logCacheTiming(request, cacheResult, now);
        } else {
            cacheResult = CacheResult.SKIPPED_RESULT;
        }

        if (isAcceptableCacheResult(cacheResult, now)) {
            return cacheResult.getResponse();
        }

        try {
            final StreamsResponse response = wrappedService.getStreams(request);
            if (isCacheable) {
                updateCache(request, response);
            }
            return response;
        } catch (IOException e) {
            return getStaleResponseOrException(cacheResult, now, e);
        }
    }

    private boolean isCacheableRequest(StreamsRequest request) {
        return !request.getDisallowCache()
                // offset must be aligned with GAME_STREAM_LIMIT, changing this will break cache keys
                && request.getStart() % GAME_STREAM_LIMIT == 0
                && request.getLimit() == GAME_STREAM_LIMIT;
    }

    private void updateCache(StreamsRequest request, StreamsResponse response) {
        final String cacheKey = getCacheKey(request);
        response = response.toBuilder()
                .setFromCache(true)
                .build();

        try {
            final RedisAsyncCommands<String, byte[]> asyncCommands = cacheClient.getAsyncCommands();
            asyncCommands.set(cacheKey, response.toByteArray());
        } catch (RedisConnectionException e) {
            // be less verbose if it's a connection problem
            LOG.warn("unable to update cache: " + e.getMessage());
        } catch (RedisException e) {
            LOG.warn("unable to update cache", e);
        }
    }

    private StreamsResponse getStaleResponseOrException(CacheResult cacheResult, long now, IOException e) throws IOException {
        if (cacheResult.getStatus() == CacheResult.Status.HIT) {
            final long cacheResultAge = getAge(now, cacheResult.getResponse().getTimestamp());
            LOG.warn("twitch api query failed, falling back on stale cache result, aged " + cacheResultAge + " ms", e);
            return cacheResult.getResponse();
        }

        // nothing to fall back on, rethrow exception
        throw e;
    }

    @Override
    public PopularGamesResponse getPopularGames(PopularGamesRequest request) throws IOException {
        // don't want to cache this at this time
        return wrappedService.getPopularGames(request);
    }

    @Nonnull
    private CacheResult getCacheResult(StreamsRequest request) {
        final String cacheKey = getCacheKey(request);

        final long cacheStart = System.currentTimeMillis();
        byte[] bytes = null;

        try {
            final RedisAsyncCommands<String, byte[]> asyncCommands = cacheClient.getAsyncCommands();
            final RedisFuture<byte[]> future = asyncCommands.get(cacheKey);
            bytes = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOG.warn("interrupted querying cache for " + getRequestString(request), e);
        } catch (RedisConnectionException e) {
            // be less verbose if it's a connection problem
            LOG.warn("error querying cache for " + getRequestString(request) + ": " + e.getMessage());
        } catch (RedisException | ExecutionException e) {
            LOG.warn("error querying cache for " + getRequestString(request), e);
        } catch (TimeoutException e) {
            // ain't nobody got time for that
            LOG.warn("timeout querying cache for " + getRequestString(request), e);
        }

        final long fetchTimeMillis = System.currentTimeMillis() - cacheStart;
        if (bytes != null) {
            try {
                return new CacheResult(StreamsResponse.parseFrom(bytes), fetchTimeMillis, CacheResult.Status.HIT);
            } catch (InvalidProtocolBufferException e) {
                LOG.warn("corrupted entry in cache for " + getRequestString(request), e);
            }
        }

        return new CacheResult(null, fetchTimeMillis, CacheResult.Status.MISS);
    }

    private String getRequestString(StreamsRequest request) {
        return "[game=" + request.getGameName() + " start=" + request.getStart() + " limit=" + request.getLimit() + "]";
    }

    private String getCacheKey(StreamsRequest request) {
        // this only works because isCacheable(StreamsRequest) requires offset alignment with GAME_STREAM_LIMIT
        final int pageNum = request.getStart() / GAME_STREAM_LIMIT;
        return CACHE_NAMESPACE + ":" + pageNum + ":" + request.getGameName();
    }

    private void logCacheTiming(StreamsRequest request, CacheResult cacheResult, long now) {
        if (LOG.isDebugEnabled()) {
            if (cacheResult.getStatus() == CacheResult.Status.HIT) {
                final long age = getAge(now, cacheResult.getResponse().getTimestamp());
                final String msgPrefix;
                if (isStale(now, cacheResult.getResponse().getTimestamp())) {
                    msgPrefix = "stale ";
                } else {
                    msgPrefix = "";
                }
                String msg = msgPrefix + "cache hit took " + cacheResult.getTimeMillis() + " ms for " + getRequestString(request) + ", result is " + age + " ms old";
                LOG.debug(msg);
            } else if (cacheResult.getStatus() == CacheResult.Status.MISS){
                LOG.debug("cache miss took " + cacheResult.getTimeMillis() + " ms for " + getRequestString(request));
            }
        }
    }

    private boolean isAcceptableCacheResult(CacheResult result, long now) {
        if (result.getStatus() == CacheResult.Status.HIT) {
            final StreamsResponse response = result.getResponse();
            if (!isStale(now, response.getTimestamp())) {
                return true;
            }
        }
        return false;
    }

    private boolean isStale(long currentTimestamp, long cacheTimestamp) {
        return currentTimestamp - cacheTimestamp > STALE_MS;
    }

    private long getAge(long currentTimestamp, long cacheTimestamp) {
        return currentTimestamp - cacheTimestamp;
    }

    // shutdown is currently called by TwitchBrowserGrpcService, maybe it should be managed as part of the bean lifecycle
    @Override
    public void shutdown() {
        LOG.info("shutting down");

        // seems like log4j2 is getting shutdown before this log message happens so we'll print to stdout too
        System.out.println("CachingTwitchApiService: shutting down");

        wrappedService.shutdown();
    }
}
