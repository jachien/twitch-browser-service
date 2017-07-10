package org.jchien.twitchbrowser.twitch;

import org.jchien.twitchbrowser.TwitchGame;
import org.jchien.twitchbrowser.TwitchStream;

import java.io.IOException;
import java.util.List;

/**
 * @author jchien
 */
public interface TwitchApiService {
    List<TwitchStream> getStreams(String gameName, int limit, boolean forceFresh) throws IOException;

    List<TwitchGame> getPopularGames(int limit) throws IOException;
}
