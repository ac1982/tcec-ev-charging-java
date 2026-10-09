package io.github.ac1982.tcec.redis;

import io.github.ac1982.tcec.security.ReplayStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Atomic shared replay admission. No live claim is removed to admit new traffic.
 * Redis must use a no-eviction policy; loss of Redis data also loses replay protection.
 * The fingerprint is an opaque authenticated request identity, never the four-digit sequence alone.
 */
public final class RedisReplayStore implements ReplayStore {
    public static final Duration MAX_CLAIM_TTL = Duration.ofDays(2).plusSeconds(1);
    private static final String CLAIM = """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local expiry = tonumber(ARGV[1])
            if expiry <= now or expiry - now > tonumber(ARGV[2]) then
                return 0
            end
            local result = redis.call('SET', KEYS[1], '1', 'NX', 'PXAT', ARGV[1])
            if result then return 1 else return 0 end
            """;
    private final RedisClient redis;
    private final String namespace;
    private final long maxTtlMillis;

    public RedisReplayStore(RedisClient redis, String namespace) {
        this(redis, namespace, MAX_CLAIM_TTL);
    }

    /** Refuses, rather than truncates, claims beyond this deployment's maximum retention window. */
    public RedisReplayStore(RedisClient redis, String namespace, Duration maxTtl) {
        this.redis = Objects.requireNonNull(redis, "Redis client is required");
        this.namespace = RedisKeys.namespace(namespace);
        if (maxTtl == null || maxTtl.isNegative() || maxTtl.compareTo(Duration.ofMillis(1)) < 0
                || maxTtl.compareTo(MAX_CLAIM_TTL) > 0) {
            throw new IllegalArgumentException("Replay TTL bound must be between one millisecond and two days plus one second");
        }
        this.maxTtlMillis = maxTtl.toMillis();
    }

    @Override
    public boolean claim(String operatorId, String timeStamp, String requestFingerprint, Instant expiresAt) {
        if (!RedisKeys.bounded(operatorId, 256) || !RedisKeys.bounded(timeStamp, 64)
                || !RedisKeys.bounded(requestFingerprint, 256) || expiresAt == null) return false;
        final long expiryMillis;
        try {
            // Round up so Redis never drops protection before the requested expiry instant.
            long floorMillis = expiresAt.toEpochMilli();
            expiryMillis = expiresAt.getNano() % 1_000_000 == 0 ? floorMillis : Math.addExact(floorMillis, 1);
        } catch (ArithmeticException invalidExpiry) {
            return false;
        }
        // Lua numbers are doubles; reject values outside the exactly representable integer range.
        if (expiryMillis < 1 || expiryMillis > 9_007_199_254_740_991L) return false;
        String key = RedisKeys.key(namespace, "replay", operatorId, timeStamp, requestFingerprint);
        try {
            Object result = redis.eval(CLAIM, List.of(key), List.of(Long.toString(expiryMillis), Long.toString(maxTtlMillis)));
            return Long.valueOf(1).equals(result);
        } catch (JedisException failure) {
            return false;
        }
    }
}
