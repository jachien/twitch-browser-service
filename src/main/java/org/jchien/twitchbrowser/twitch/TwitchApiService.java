package org.jchien.twitchbrowser.twitch;

import org.jchien.twitchbrowser.PopularGamesRequest;
import org.jchien.twitchbrowser.PopularGamesResponse;
import org.jchien.twitchbrowser.StreamsRequest;
import org.jchien.twitchbrowser.StreamsResponse;

import java.io.IOException;
import java.util.List;

/**
 * @author jchien
 */
public interface TwitchApiService {
    StreamsResponse getStreams(StreamsRequest request) throws IOException;

    PopularGamesResponse getPopularGames(PopularGamesRequest request) throws IOException;

    void shutdown();
}
