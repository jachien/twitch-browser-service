package org.jchien.twitchbrowser.health;

import org.jchien.twitchbrowser.StreamsRequest;
import org.jchien.twitchbrowser.twitch.TwitchApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author jchien
 */
@Component
public class KrakenHealthCheck implements HealthIndicator {
    private TwitchApiService service;

    @Autowired
    public KrakenHealthCheck(@Qualifier("basicTwitchApiService")  TwitchApiService service) {
        this.service = service;
    }

    @Override
    public Health health() {
        StreamsRequest request = StreamsRequest.newBuilder()
                .setGameName("Dota 2")
                .setStart(0)
                .setLimit(25)
                .build();

        try {
            service.getStreams(request);
            // todo sanity check response
            return Health.up().build();
        } catch (IOException e) {
            return Health.down().withException(e).build();
        }
    }
}
