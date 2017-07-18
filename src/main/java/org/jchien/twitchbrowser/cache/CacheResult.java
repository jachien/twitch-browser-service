package org.jchien.twitchbrowser.cache;

import org.jchien.twitchbrowser.StreamsResponse;

/**
 * @author jchien
 */
public class CacheResult {
    public enum Status {
        HIT,
        MISS,
        SKIPPED
    }

    private final StreamsResponse response;
    private final long timeMillis;
    private final Status status;

    public static final CacheResult SKIPPED_RESULT = new CacheResult(null, 0L, Status.SKIPPED);

    public CacheResult(StreamsResponse response, long timeMillis, Status status) {
        this.response = response;
        this.timeMillis = timeMillis;
        this.status = status;
    }

    public StreamsResponse getResponse() {
        return response;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public Status getStatus() {
        return status;
    }
}
