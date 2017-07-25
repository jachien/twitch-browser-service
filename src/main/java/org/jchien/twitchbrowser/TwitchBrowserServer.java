package org.jchien.twitchbrowser;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.jchien.twitchbrowser.twitch.BasicTwitchApiService;
import org.jchien.twitchbrowser.twitch.CachingTwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author jchien
 */
public class TwitchBrowserServer {
    private static final Logger LOG = LoggerFactory.getLogger(TwitchBrowserServer.class);

    private final int port;
    private final TwitchApiService service;
    private final Server server;

    public static void main(String[] args) throws Exception {
        TwitchBrowserServer server = new TwitchBrowserServer(62898);
        server.start();
        server.blockUntilShutdown();
    }

    public TwitchBrowserServer(int port) {
        this(ServerBuilder.forPort(port), port);
    }

    public TwitchBrowserServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;

        final TwitchApiService service = new CachingTwitchApiService(new BasicTwitchApiService());
        this.service = service;

        this.server = serverBuilder.addService(new TwitchBrowserService(service))
                .build();
    }

    /** Start serving requests. */
    public void start() throws IOException {
        server.start();
        LOG.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may has been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                TwitchBrowserServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    /** Stop serving requests and shutdown resources. */
    public void stop() {
        if (service != null) {
            service.shutdown();
        }

        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private static class TwitchBrowserService extends TwitchBrowserServiceGrpc.TwitchBrowserServiceImplBase {
        private final TwitchApiService twitchApiService;

        public TwitchBrowserService(TwitchApiService twitchApiService) {
            this.twitchApiService = twitchApiService;
        }

        @Override
        public void getStreams(StreamsRequest request, StreamObserver<StreamsResponse> responseObserver) {
            try {
                StreamsResponse response = twitchApiService.getStreams(request);
                responseObserver.onNext(response);
            } catch (IOException e) {
                // what happens if multiple errors are sent?
                responseObserver.onError(e);
            }

            responseObserver.onCompleted();
        }

        @Override
        public void getPopularGames(PopularGamesRequest request, StreamObserver<PopularGamesResponse> responseObserver) {
            try {
                PopularGamesResponse response = twitchApiService.getPopularGames(request);
                responseObserver.onNext(response);
            } catch (IOException e) {
                responseObserver.onError(e);
            }
            responseObserver.onCompleted();
        }
    }
}
