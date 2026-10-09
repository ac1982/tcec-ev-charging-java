package io.github.ac1982.tcec.security;

import io.github.ac1982.tcec.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Bounded single-JVM token store. Concurrent issuance for a partner reuses its live token without
 * extending expiry. Capacity exhaustion never evicts a live partner's token to admit another.
 */
public final class InMemoryTokenStore implements TokenStore {
    private static final int DEFAULT_CAPACITY = 100_000;
    private record Expiration(String operatorId, AccessToken token) {}
    private final Clock clock;
    private final int capacity;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, AccessToken> tokens = new HashMap<>();
    private final PriorityQueue<Expiration> expirations = new PriorityQueue<>(
            Comparator.comparing(expiration -> expiration.token().expiresAt()));

    public InMemoryTokenStore(Clock clock) { this(clock, DEFAULT_CAPACITY); }

    public InMemoryTokenStore(Clock clock, int capacity) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    @Override public synchronized AccessToken issue(String operatorId, Duration ttl) {
        if (operatorId == null || !operatorId.matches("[A-Za-z0-9]{9}") || ttl == null
                || ttl.isNegative() || ttl.isZero() || ttl.getNano() != 0
                || ttl.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Valid partner and whole-second TTL from 1 second to 7 days required");
        }
        Instant now = clock.instant();
        AccessToken existing = tokens.get(operatorId);
        if (existing != null && existing.expiresAt().isAfter(now)) return existing;
        removeExpired(now);
        if (tokens.size() >= capacity) throw new ProtocolException(-1, "Token store capacity exhausted");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var token = new AccessToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), now.plus(ttl));
        tokens.put(operatorId, token);
        expirations.add(new Expiration(operatorId, token));
        return token;
    }

    @Override public synchronized boolean validate(String operatorId, String supplied) {
        if (operatorId == null || supplied == null || supplied.isBlank() || supplied.length() > 4096) return false;
        AccessToken expected = tokens.get(operatorId);
        if (expected == null) return false;
        Instant now = clock.instant();
        if (!expected.expiresAt().isAfter(now)) { removeExpired(now); return false; }
        return MessageDigest.isEqual(expected.value().getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void removeExpired(Instant now) {
        // All removals drain the queue too, keeping one entry per token even after clock rollback.
        while (!expirations.isEmpty() && !expirations.peek().token().expiresAt().isAfter(now)) {
            Expiration expired = expirations.remove();
            tokens.remove(expired.operatorId(), expired.token());
        }
    }
}
