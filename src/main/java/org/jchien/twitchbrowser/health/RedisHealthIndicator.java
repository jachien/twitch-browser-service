package org.jchien.twitchbrowser.health;

import com.google.common.primitives.Longs;
import com.lambdaworks.redis.RedisFuture;
import com.lambdaworks.redis.api.async.RedisAsyncCommands;
import org.jchien.twitchbrowser.cache.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author jchien
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {
    private final static String HEALTH_KEY = "healthcheck";

    private final static long TIMEOUT_MILLIS = 1000L;

    private CacheClient cacheClient;

    @Autowired
    public RedisHealthIndicator(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    @Override
    public Health health() {
        try {
            long now = System.currentTimeMillis();

            RedisAsyncCommands<String, byte[]> asyncCmds = cacheClient.getAsyncCommands();

            byte[] bytes = Longs.toByteArray(now);
            RedisFuture<String> setFuture = asyncCmds.set(HEALTH_KEY, bytes);
            // block until successful set or timeout
            String setResult = setFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!"OK".equals(setResult)) {
                return Health.down().withDetail("set_result", setResult).build();
            }

            RedisFuture<byte[]> getFuture = asyncCmds.get(HEALTH_KEY);
            byte[] getResult = getFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            long result = Longs.fromByteArray(getResult);

            if (now != result) {
                return Health.down().withDetail("expected", now).withDetail("actual", result).build();
            }

            long elapsed = System.currentTimeMillis() - now;
            return Health.up().withDetail("time", elapsed).build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }

    }
}
