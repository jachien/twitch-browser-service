package org.jchien.twitchbrowser.twitch;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.*;
import org.jchien.twitchbrowser.PopularGamesRequest;
import org.jchien.twitchbrowser.PopularGamesResponse;
import org.jchien.twitchbrowser.StreamsRequest;
import org.jchien.twitchbrowser.StreamsResponse;
import org.jchien.twitchbrowser.json.ProtoJsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * @author jchien
 */
@Component("basicTwitchApiService")
public class BasicTwitchApiService implements TwitchApiService {
    private static final Logger LOG = LoggerFactory.getLogger(BasicTwitchApiService.class);

    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(TwitchStream.class, new ProtoJsonDeserializer<>(TwitchStream.class))
            .registerTypeAdapter(TwitchGame.class, new ProtoJsonDeserializer<>(TwitchGame.class))
            .create();

    private static final String HOST = "https://api.twitch.tv";

    private static final String STREAMS_ENDPOINT = "/kraken/streams";

    private static final String POPULAR_GAMES_ENDPOINT = "/kraken/games/top";

    private static final int CONNECT_TIMEOUT_MS = 1000;

    private static final int READ_TIMEOUT_MS = 3000;

    private static final int MAX_LIMIT = 25;

    private String twitchApiClientId;

    @Autowired
    public BasicTwitchApiService(@Qualifier("twitchApiClientId") String twitchApiClientId) {
        this.twitchApiClientId = twitchApiClientId;
    }

    private HttpRequest buildGetRequest(GenericUrl url) throws IOException {
        final HttpRequestFactory httpReqFactory = HTTP_TRANSPORT.createRequestFactory();

        final HttpHeaders headers = new HttpHeaders()
                .setAccept("application/vnd.twitchtv.v5+json")
                .setAcceptEncoding("UTF-8")
                .set("Client-ID", twitchApiClientId);

        final HttpRequest httpReq = httpReqFactory.buildGetRequest(url)
                .setHeaders(headers)
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setReadTimeout(READ_TIMEOUT_MS);

        return httpReq;
    }

    private <T> T parseResponse(HttpResponse httpResp, JsonResponseHandler<T> responseHandler) throws IOException {
        if (200 != httpResp.getStatusCode()) {
            throw new IOException("unable to parse stream, error code " + httpResp.getStatusCode());
        }

        // http response is claiming ISO-8859-1 charset, but it should be UTF-8 since the content type is application/json
        final Charset contentCharset = Charset.forName("UTF-8");
        final InputStream is = httpResp.getContent();

        final String content = readString(is, contentCharset);

        final JsonParser jsonParser = new JsonParser();
        final JsonElement json = jsonParser.parse(content);
        final JsonObject root = json.getAsJsonObject();
        return responseHandler.handle(root, content);
    }

    private String readString(InputStream is, Charset contentCharset) throws IOException {
        final int bufSize = 1024;
        final byte[] buf = new byte[bufSize];
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);

        int bytesRead;
        do {
            bytesRead = is.read(buf, 0, bufSize);
            if (bytesRead > 0) {
                baos.write(buf, 0, bytesRead);
            }
        } while (bytesRead > 0);

        return new String(baos.toByteArray(), contentCharset);
    }

    @Override
    public StreamsResponse getStreams(StreamsRequest request) throws IOException {
        final String gameName = request.getGameName();
        final int limit = Math.min(MAX_LIMIT, request.getLimit());

        final GenericUrl url = buildStreamsUrl(gameName, limit);
        final HttpRequest httpReq = buildGetRequest(url);

        final HttpResponse httpResp;

        final long start = System.currentTimeMillis();
        try {
            httpResp = httpReq.execute();
        } catch (Exception e) {
            throw new IOException("failed to make http request for \"" + gameName + "\" to " + httpReq.getUrl(), e);
        }
        final long elapsed = System.currentTimeMillis() - start;
        LOG.info("took " + elapsed + " ms to make query for \"" + gameName + "\"");

        return parseResponse(httpResp, new StreamsHandler(gameName));
    }

    private GenericUrl buildStreamsUrl(String gameName, int limit) {
        return new GenericUrl(HOST + STREAMS_ENDPOINT)
                .set("game", gameName)
                .set("limit", limit);
    }

    @Override
    public PopularGamesResponse getPopularGames(PopularGamesRequest request) throws IOException {
        final GenericUrl url = buildPopularGamesUrl(request.getLimit());
        final HttpRequest httpReq = buildGetRequest(url);

        final HttpResponse httpResp;

        final long start = System.currentTimeMillis();
        try {
            httpResp = httpReq.execute();
        } catch (Exception e) {
            throw new IOException("failed to make http request for popular games to " + httpReq.getUrl(), e);
        }
        final long elapsed = System.currentTimeMillis() - start;
        LOG.info("took " + elapsed + " ms to get popular games");

        return parseResponse(httpResp, new GamesHandler());
    }

    private GenericUrl buildPopularGamesUrl(int limit) {
        return new GenericUrl(HOST + POPULAR_GAMES_ENDPOINT)
                .set("limit", limit);
    }

    private interface JsonResponseHandler<T> {
        T handle(JsonObject root, String rawJson);
    }

    private static class StreamsHandler implements JsonResponseHandler<StreamsResponse> {
        final String gameName;

        private StreamsHandler(String gameName) {
            this.gameName = gameName;
        }

        @Override
        public StreamsResponse handle(JsonObject root, String rawJson) {
            final long resultTimestamp = System.currentTimeMillis();
            final StreamsResponse.Builder response = StreamsResponse.newBuilder()
                    .setTimestamp(resultTimestamp)
                    .setFromCache(false);

            final JsonArray streams = root.getAsJsonArray("streams");
            for (JsonElement stream : streams) {
                try {
                    final String json = GSON.toJson(stream);
                    final TwitchStream tsm = GSON.fromJson(json, TwitchStream.class);
                    response.addStreams(tsm);
                    if (!TwitchResponseValidator.isValid(tsm)) {
                        LOG.warn("Bad stream " + tsm + "\n from \n" + rawJson);
                    }
                } catch (Exception e) {
                    LOG.error("failed to parse results for query " + gameName + ":\n" + root, e);
                }
            }
            return response.build();
        }
    }

    private static class GamesHandler implements JsonResponseHandler<PopularGamesResponse> {
        @Override
        public PopularGamesResponse handle(JsonObject root, String rawJson) {
            final PopularGamesResponse.Builder response = PopularGamesResponse.newBuilder();
            final JsonArray topGames = root.getAsJsonArray("top");
            for (JsonElement gameJson : topGames) {
                try {
                    final String json = GSON.toJson(gameJson);
                    final TwitchGame game = GSON.fromJson(json, TwitchGame.class);
                    response.addGames(game);
                } catch (Exception e) {
                    LOG.error("failed to parse top games", e);
                }
            }
            return response.build();
        }
    }

    @Override
    public void shutdown() {
    }
}
