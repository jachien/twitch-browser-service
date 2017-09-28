package org.jchien.twitchbrowser;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.jchien.twitchbrowser.twitch.CachingTwitchApiService;
import org.jchien.twitchbrowser.twitch.TwitchApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;

/**
 * @author jchien
 */
@Component
public class TwitchBrowserGrpcService implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TwitchBrowserGrpcService.class);

    private final int port;
    private final TwitchApiService service;
    private final Server server;

    @Autowired
    public TwitchBrowserGrpcService(int port, CachingTwitchApiService service) {
        this(ServerBuilder.forPort(port), port, service);
    }

    public TwitchBrowserGrpcService(ServerBuilder<?> serverBuilder, int port, TwitchApiService service) {
        this.port = port;
        this.service = service;
        this.server = serverBuilder.addService(new TwitchBrowserService(service))
                .build();
    }

    @Override
    public void run(String... args) throws Exception {
        start();
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
                TwitchBrowserGrpcService.this.stop();
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
    @PreDestroy
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
