package org.jchien.twitchbrowser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This feels incredibly dumb, but if I don't have TwitchBrowserServerProperties separate from this config class,
 * having @Beans was creating beans with default values before they were read from the properties file.
 *
 * Having the properties read into its own class, then autowiring it here seems to avoid that problem.
 *
 * @author jchien
 */

@Configuration
public class TwitchBrowserServerConfig {
    @Autowired
    private TwitchBrowserServerProperties props;

    @Bean(name="port")
    public int getPort() {
        return props.getPort();
    }

    @Bean(name="twitchApiClientId")
    public String getTwitchApiClientId() {
        return props.getTwitchApiClientId();
    }

    @Bean(name="redisUri")
    public String getRedisUri() {
        return props.getRedisUri();
    }
}
