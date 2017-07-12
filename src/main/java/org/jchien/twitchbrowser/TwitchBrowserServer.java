package org.jchien.twitchbrowser;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.jchien.twitchbrowser.twitch.BasicTwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchGame;
import org.jchien.twitchbrowser.twitch.TwitchStream;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author jchien
 */
public class TwitchBrowserServer {
    private static final Logger LOG = Logger.getLogger(TwitchBrowserServer.class.getName());

    private final int port;
    private final Server server;

    public TwitchBrowserServer(int port) {
        this(ServerBuilder.forPort(port), port);
    }

    public TwitchBrowserServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;
        server = serverBuilder.addService(new TwitchBrowserService(new BasicTwitchApiService()))
                .build();
    }

    public void start() throws IOException {
        server.start();
        LOG.info("Server started, listening on " + port);
    }

    private static class TwitchBrowserService extends TwitchBrowserServiceGrpc.TwitchBrowserServiceImplBase {
        private final TwitchApiService twitchApiService;

        public TwitchBrowserService(TwitchApiService twitchApiService) {
            this.twitchApiService = twitchApiService;
        }

        @Override
        public void getStreams(StreamsRequest request, StreamObserver<StreamsResponse> responseObserver) {
            super.getStreams(request, responseObserver);

            StreamsResponse.Builder builder = StreamsResponse.newBuilder();
            for (String gameName : request.getGameNamesList()) {
                try {
                    List<TwitchStream> streams = twitchApiService.getStreams(gameName, request.getLimit(), request.getForceFresh());
                    builder.addAllStreams(streams);
                } catch (IOException e) {
                    // what happens if multiple errors are sent?
                    responseObserver.onError(e);
                }
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void getPopularGames(PopularGamesRequest request, StreamObserver<PopularGamesResponse> responseObserver) {
            super.getPopularGames(request, responseObserver);

            try {
                List<TwitchGame> games = twitchApiService.getPopularGames(request.getLimit());
                PopularGamesResponse response = PopularGamesResponse.newBuilder()
                        .addAllGames(games)
                        .build();
                responseObserver.onNext(response);
            } catch (IOException e) {
                responseObserver.onError(e);
            }
            responseObserver.onCompleted();
        }
    }
}
