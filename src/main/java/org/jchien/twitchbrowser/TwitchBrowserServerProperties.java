package org.jchien.twitchbrowser;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Read class comment on TwitchBrowserServerConfig.
 *
 * @author jchien
 */
@Configuration
@ConfigurationProperties(prefix="twibro")
public class TwitchBrowserServerProperties {
    private int port;

    private String twitchApiClientId;

    private String redisUri;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getTwitchApiClientId() {
        return twitchApiClientId;
    }

    public void setTwitchApiClientId(String twitchApiClientId) {
        this.twitchApiClientId = twitchApiClientId;
    }

    public String getRedisUri() {
        return redisUri;
    }

    public void setRedisUri(String redisUri) {
        this.redisUri = redisUri;
    }
}
