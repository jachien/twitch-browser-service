package org.jchien.twitchbrowser;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
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
        server = serverBuilder.addService(new TwitchBrowserService())
                .build();
    }

    public void start() throws IOException {
        server.start();
        LOG.info("Server started, listening on " + port);
    }

    private static class TwitchBrowserService extends TwitchBrowserServiceGrpc.TwitchBrowserServiceImplBase {
        @Override
        public void getStreams(StreamsRequest request, StreamObserver<StreamsResponse> responseObserver) {
            super.getStreams(request, responseObserver);
            responseObserver.onNext(null);
            responseObserver.onCompleted();
        }

        @Override
        public void getPopularGames(PopularGamesRequest request, StreamObserver<PopularGamesResponse> responseObserver) {
            super.getPopularGames(request, responseObserver);
            responseObserver.onNext(null);
            responseObserver.onCompleted();
        }
    }
}
