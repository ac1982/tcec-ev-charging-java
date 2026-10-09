package io.github.ac1982.tcec.redis;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;

class RedisStoreValidationTest {
    @Test void validatesNamespaceWithoutConnecting() {
        try (var redis = RedisClient.create("127.0.0.1", 1)) {
            for (String namespace : new String[] {"", "  ", "bad*", "bad{tag}", "x".repeat(65)}) {
                assertThrows(IllegalArgumentException.class, () -> new RedisTokenStore(redis, namespace));
                assertThrows(IllegalArgumentException.class, () -> new RedisReplayStore(redis, namespace));
            }
            assertThrows(IllegalArgumentException.class, () -> new RedisTokenStore(redis, null));
        }
    }

    @Test void rejectsUnboundedOrMalformedInputsBeforeRedis() {
        try (var redis = RedisClient.create("127.0.0.1", 1)) {
            var tokens = new RedisTokenStore(redis, "unit");
            for (Duration ttl : new Duration[] {Duration.ZERO, Duration.ofNanos(1), Duration.ofMillis(1001),
                    Duration.ofSeconds(-1), Duration.ofDays(7).plusSeconds(1)}) {
                assertThrows(IllegalArgumentException.class, () -> tokens.issue("operator", ttl));
            }
            assertThrows(IllegalArgumentException.class, () -> tokens.issue("operator", null));
            assertThrows(IllegalArgumentException.class, () -> tokens.issue("x".repeat(257), Duration.ofSeconds(1)));
            assertThrows(IllegalArgumentException.class, () -> tokens.issue(null, Duration.ofSeconds(1)));
            assertFalse(tokens.validate("operator", "x".repeat(4097)));
            assertFalse(tokens.validate(null, "token"));
            assertFalse(tokens.validate("operator", " "));
            var replays = new RedisReplayStore(redis, "unit");
            assertFalse(replays.claim("x".repeat(257), "ts", "id", Instant.now().plusSeconds(2)));
            assertFalse(replays.claim("op", "x".repeat(65), "id", Instant.now().plusSeconds(2)));
            assertFalse(replays.claim("op", "ts", "x".repeat(257), Instant.now().plusSeconds(2)));
            assertFalse(replays.claim("op", "ts", "id", Instant.MAX));
            assertFalse(replays.claim("op", "ts", "id", Instant.MIN));
            assertFalse(replays.claim("op", "ts", "id", null));
            assertThrows(IllegalArgumentException.class, () -> new RedisReplayStore(redis, "unit", Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> new RedisReplayStore(redis, "unit", Duration.ofDays(3)));
        }
    }

    @Test void hashesLengthFramedKeyPartsWithDomainAndNamespaceSeparation() {
        String key = RedisKeys.key("service-inbound", "replay", "secret-operator", "time", "fingerprint");
        assertTrue(key.matches("service-inbound:replay:[a-f0-9]{64}"));
        assertFalse(key.contains("secret-operator"));
        assertNotEquals(RedisKeys.key("n", "replay", "a:b", "c", "d"), RedisKeys.key("n", "replay", "a", "b:c", "d"));
        assertNotEquals(RedisKeys.key("n", "replay", "a", "b", "c"), RedisKeys.key("n", "replay", "ab", "", "c"));
        assertNotEquals(RedisKeys.key("n", "token", "a"), RedisKeys.key("n", "replay", "a"));
        assertNotEquals(RedisKeys.key("n1", "token", "a"), RedisKeys.key("n2", "token", "a"));
    }
}
