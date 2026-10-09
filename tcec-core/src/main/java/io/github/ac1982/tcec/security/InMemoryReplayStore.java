package io.github.ac1982.tcec.security;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
/** Bounded, atomic single-JVM replay cache. Never evicts an unexpired claim to admit new traffic. */
public final class InMemoryReplayStore implements ReplayStore {
    private record Key(String operatorId, String timeStamp, String requestFingerprint) {}
    private record Expiration(Key key, Instant expiresAt) {}
    private final Clock clock;
    private final int capacity;
    private final Map<Key, Instant> claims = new HashMap<>();
    private final PriorityQueue<Expiration> expirations = new PriorityQueue<>(Comparator.comparing(Expiration::expiresAt));
    public InMemoryReplayStore(Clock clock, int capacity) { this.clock = Objects.requireNonNull(clock); if (capacity < 1) throw new IllegalArgumentException("capacity must be positive"); this.capacity = capacity; }
    @Override public synchronized boolean claim(String operatorId, String timeStamp, String requestFingerprint, Instant expiresAt) {
        Instant now = clock.instant();
        while (!expirations.isEmpty() && !expirations.peek().expiresAt().isAfter(now)) {
            Expiration expired = expirations.remove();
            claims.remove(expired.key(), expired.expiresAt());
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) return false;
        var key = new Key(operatorId, timeStamp, requestFingerprint);
        if (claims.containsKey(key) || claims.size() >= capacity) return false;
        claims.put(key, expiresAt);
        // Only successful admissions enqueue: both structures remain bounded by capacity.
        expirations.add(new Expiration(key, expiresAt));
        return true;
    }
}
