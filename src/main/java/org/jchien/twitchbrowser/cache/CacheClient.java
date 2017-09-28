package org.jchien.twitchbrowser.cache;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisException;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;

/**
 * @author jchien
 */
@Component
public class CacheClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CacheClient.class);

    private final RedisClient redisClient;

    private volatile StatefulRedisConnection<String, byte[]> redisConnection;

    @Autowired
    public CacheClient(@Qualifier("redisUri") String redisUri) {
        this.redisClient = RedisClient.create(redisUri);
        try {
            this.redisConnection = getConnection();
        } catch (RedisException e) {
            LOG.warn("unable to establish initial redis connection", e);
        }
    }

    private StatefulRedisConnection<String, byte[]> getConnection() {
        if (redisConnection == null) {
            synchronized(this) {
                if (redisConnection == null) {
                    redisConnection = redisClient.connect(StringByteArrayCodec.INSTANCE);
                }
            }
        }
        return redisConnection;
    }

    public RedisAsyncCommands<String, byte[]> getAsyncCommands() {
        StatefulRedisConnection<String, byte[]> conn = getConnection();
        if (conn != null) {
            return conn.async();
        }
        throw new RedisException("connection to redis unavailable");
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        LOG.info("shutting down redis connection and client");

        // seems like log4j2 is getting shutdown before this log message happens so we'll print to stdout too
        System.out.println("CacheClient: shutting down");

        if (redisConnection != null) {
            redisConnection.close();
        }
        redisClient.shutdown();
    }
}
