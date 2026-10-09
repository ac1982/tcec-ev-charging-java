package io.github.ac1982.tcec.redis;

import static org.junit.jupiter.api.Assertions.*;
import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.security.AccessToken;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;

/** Real Redis, two independent pooled clients, no mocks, Docker, or externally managed server. */
@Timeout(20)
class RedisSecurityStoresIT {
    @TempDir static Path directory;
    private static Process process;
    private static RedisClient first;
    private static RedisClient second;

    @BeforeAll @Timeout(12) static void startLocalRedis() throws Exception {
        int port = unusedPort();
        String executable = System.getProperty("redis.server.executable", "redis-server");
        try {
            process = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", Integer.toString(port),
                    "--protected-mode", "yes", "--save", "", "--appendonly", "no", "--daemonize", "no",
                    "--maxmemory", "64mb", "--maxmemory-policy", "noeviction", "--dir", directory.toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        } catch (IOException unavailable) {
            throw new AssertionError("Real Redis integration requires redis-server 7.2+ on PATH or -Dredis.server.executable=/absolute/path. No tests were skipped.");
        }
        first = client(port);
        second = client(port);
        await(() -> {
            if (!process.isAlive()) throw new AssertionError("Local test Redis exited before readiness");
            try { return "PONG".equals(first.ping()); }
            catch (RuntimeException notReady) { return false; }
        }, Duration.ofSeconds(8));
    }

    @AfterAll @Timeout(12) static void stopLocalRedis() throws Exception {
        if (first != null) first.close();
        if (second != null) second.close();
        if (process != null) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Test Redis must terminate");
            }
        }
    }

    @Test void reusesLiveTokenAcrossClientsWithoutExtendingExpiry() {
        String namespace = namespace();
        var store1 = new RedisTokenStore(first, namespace);
        var store2 = new RedisTokenStore(second, namespace);
        AccessToken original = store1.issue("123456789", Duration.ofSeconds(10));
        AccessToken reused = store2.issue("123456789", Duration.ofDays(7));
        assertEquals(original, reused);
        assertTrue(store2.validate("123456789", original.value()));
        assertFalse(store2.validate("123456789", original.value() + "wrong"));
        String key = RedisKeys.key(namespace, "token", "123456789");
        assertTrue(first.pttl(key) > 0 && first.pttl(key) <= 10_000);
        assertEquals(original.expiresAt().toEpochMilli(), Long.parseLong(first.hget(key, "expires")));
    }

    @Test void concurrentTokenIssueReturnsExactlyOneSharedToken() throws Exception {
        String namespace = namespace();
        var one = new RedisTokenStore(first, namespace);
        var two = new RedisTokenStore(second, namespace);
        var results = concurrently(64, index -> (index % 2 == 0 ? one : two).issue("123456789", Duration.ofSeconds(10)));
        assertEquals(1, new HashSet<>(results).size());
        assertTrue(one.validate("123456789", results.getFirst().value()));
    }

    @Test void expiredTokenIsRejectedAndReissuedWithFreshExpiry() throws Exception {
        String namespace = namespace();
        var one = new RedisTokenStore(first, namespace);
        var two = new RedisTokenStore(second, namespace);
        AccessToken old = one.issue("123456789", Duration.ofSeconds(1));
        await(() -> !two.validate("123456789", old.value()), Duration.ofSeconds(5));
        AccessToken replacement = two.issue("123456789", Duration.ofSeconds(2));
        assertNotEquals(old.value(), replacement.value());
        assertTrue(replacement.expiresAt().isAfter(old.expiresAt()));
        assertFalse(one.validate("123456789", old.value()));
        assertTrue(one.validate("123456789", replacement.value()));
    }

    @Test void tokenNamespacesAndOperatorsAreIsolated() {
        String namespace = namespace();
        var inbound = new RedisTokenStore(first, namespace);
        var outbound = new RedisTokenStore(second, namespace + "-out");
        AccessToken a = inbound.issue("123456789", Duration.ofSeconds(10));
        AccessToken b = inbound.issue("987654321", Duration.ofSeconds(10));
        AccessToken c = outbound.issue("123456789", Duration.ofSeconds(10));
        assertFalse(inbound.validate("987654321", a.value()));
        assertFalse(outbound.validate("123456789", a.value()));
        assertFalse(inbound.validate("123456789", b.value()));
        assertFalse(inbound.validate("123456789", c.value()));
        assertEquals(3, new HashSet<>(List.of(a.value(), b.value(), c.value())).size());
    }

    @Test void concurrentReplayClaimHasExactlyOneWinnerAcrossClients() throws Exception {
        String namespace = namespace();
        var one = new RedisReplayStore(first, namespace);
        var two = new RedisReplayStore(second, namespace);
        Instant expiry = Instant.now().plusSeconds(10);
        List<Boolean> admitted = concurrently(64,
                index -> (index % 2 == 0 ? one : two).claim("123456789", "20261009092200", "a".repeat(64), expiry));
        assertEquals(1, admitted.stream().filter(Boolean::booleanValue).count());
        long ttl = first.pttl(RedisKeys.key(namespace, "replay", "123456789", "20261009092200", "a".repeat(64)));
        assertTrue(ttl > 0 && ttl <= 10_001);
    }

    @Test void replayIdentityUsesAllPartsAndPreservesNamespaceAndPeerIsolation() {
        String namespace = namespace();
        var one = new RedisReplayStore(first, namespace);
        var two = new RedisReplayStore(second, namespace);
        var other = new RedisReplayStore(second, namespace + "-out");
        Instant expiry = Instant.now().plusSeconds(10);
        assertTrue(one.claim("123456789", "20261009092200", "a".repeat(64), expiry));
        assertFalse(two.claim("123456789", "20261009092200", "a".repeat(64), expiry));
        assertTrue(two.claim("123456789", "20261009092200", "b".repeat(64), expiry));
        assertTrue(two.claim("987654321", "20261009092200", "a".repeat(64), expiry));
        assertTrue(two.claim("123456789", "20261009092201", "a".repeat(64), expiry));
        assertTrue(other.claim("123456789", "20261009092200", "a".repeat(64), expiry));
        assertTrue(one.claim("a:b", "c", "d", expiry));
        assertTrue(one.claim("a", "b:c", "d", expiry));
    }

    @Test void replayExpiresWithoutAnyExtensionByDuplicateAttempts() throws Exception {
        String namespace = namespace();
        var store = new RedisReplayStore(first, namespace);
        assertTrue(store.claim("op", "ts", "fingerprint", Instant.now().plusMillis(250)));
        assertFalse(store.claim("op", "ts", "fingerprint", Instant.now().plusSeconds(10)));
        String key = RedisKeys.key(namespace, "replay", "op", "ts", "fingerprint");
        assertTrue(first.pttl(key) <= 251);
        await(() -> !first.exists(key), Duration.ofSeconds(5));
        assertTrue(store.claim("op", "ts", "fingerprint", Instant.now().plusSeconds(1)));
    }

    @Test void replayRejectsPastAndOverlongClaimsInsteadOfTruncatingRetention() {
        String namespace = namespace();
        var store = new RedisReplayStore(first, namespace, Duration.ofSeconds(1));
        assertFalse(store.claim("op", "ts", "expired", Instant.now().minusSeconds(1)));
        assertFalse(store.claim("op", "ts", "too-long", Instant.now().plusSeconds(10)));
        assertFalse(new RedisReplayStore(first, namespace).claim("op", "ts", "days", Instant.now().plus(Duration.ofDays(3))));
        assertTrue(first.keys(namespace + ":*").isEmpty());
        assertTrue(store.claim("op", "ts", "valid", Instant.now().plusMillis(900)));
    }

    @Test void memoryCapacityRefusalFailsClosedWithoutEvictingExistingReplay() {
        String namespace = namespace();
        var replays = new RedisReplayStore(first, namespace);
        assertTrue(replays.claim("op", "ts", "kept", Instant.now().plusSeconds(30)));
        String kept = RedisKeys.key(namespace, "replay", "op", "ts", "kept");
        var fillKeys = new ArrayList<String>();
        boolean refused = false;
        try {
            String payload = "x".repeat(1024 * 1024);
            for (int i = 0; i < 80; i++) {
                String key = namespace + ":capacity:" + i;
                fillKeys.add(key);
                try { first.set(key, payload); }
                catch (JedisDataException full) {
                    assertTrue(full.getMessage().startsWith("OOM"), "Expected local noeviction memory refusal");
                    refused = true;
                    break;
                }
            }
            assertTrue(refused, "Test server's 64 MiB memory limit must refuse writes");
            // A refused large SET can leave room for small claims after its input buffer is freed.
            // Fill with real claims until a claim itself observes the noeviction refusal.
            boolean claimRefused = false;
            for (int i = 0; i < 10_000; i++) {
                String fingerprint = "capacity-claim-" + i;
                fillKeys.add(RedisKeys.key(namespace, "replay", "op", "ts", fingerprint));
                if (!replays.claim("op", "ts", fingerprint, Instant.now().plusSeconds(30))) {
                    claimRefused = true;
                    break;
                }
            }
            assertTrue(claimRefused, "Replay admission must refuse once small writes exhaust memory");
            assertTrue(first.exists(kept), "Capacity pressure must not evict live replay claims");
            boolean tokenRefused = false;
            var tokens = new RedisTokenStore(first, namespace);
            for (int i = 0; i < 100; i++) {
                String operator = "capacity-operator-" + i;
                fillKeys.add(RedisKeys.key(namespace, "token", operator));
                try { tokens.issue(operator, Duration.ofSeconds(30)); }
                catch (ProtocolException full) {
                    assertEquals(ProtocolException.SYSTEM_ERROR, full.ret());
                    tokenRefused = true;
                    break;
                }
            }
            assertTrue(tokenRefused, "Token issuance must fail closed when Redis refuses writes");
        } finally {
            for (String key : fillKeys) first.del(key);
        }
        assertTrue(first.exists(kept));
        assertFalse(replays.claim("op", "ts", "kept", Instant.now().plusSeconds(10)));
        assertTrue(replays.claim("op", "ts", "after-recovery", Instant.now().plusSeconds(10)));
    }

    @Test void redisOutageFailsClosedWithoutLeakingConnectionInformation() throws Exception {
        try (var unavailable = client(unusedPort())) {
            var tokens = new RedisTokenStore(unavailable, namespace());
            var error = assertThrows(ProtocolException.class, () -> tokens.issue("123456789", Duration.ofSeconds(1)));
            assertEquals(ProtocolException.SYSTEM_ERROR, error.ret());
            assertEquals("Token store unavailable", error.getMessage());
            assertNull(error.getCause());
            assertFalse(tokens.validate("123456789", "secret-token"));
            assertFalse(new RedisReplayStore(unavailable, namespace()).claim("op", "ts", "fp", Instant.now().plusSeconds(1)));
        }
    }

    @Test void corruptAndNonExpiringTokenStateFailsClosed() {
        String namespace = namespace();
        var store = new RedisTokenStore(first, namespace);
        String key = RedisKeys.key(namespace, "token", "123456789");
        first.set(key, "wrong-type");
        assertFalse(store.validate("123456789", "wrong-type"));
        assertThrows(ProtocolException.class, () -> store.issue("123456789", Duration.ofSeconds(1)));
        first.del(key);
        first.hset(key, "token", "stale-token");
        first.hset(key, "expires", Long.toString(Instant.now().plusSeconds(30).toEpochMilli()));
        assertFalse(store.validate("123456789", "stale-token"));
        assertThrows(ProtocolException.class, () -> store.issue("123456789", Duration.ofSeconds(1)));
    }

    private static RedisClient client(int port) {
        var pool = new GenericObjectPoolConfig<Connection>();
        pool.setMaxTotal(16);
        pool.setMaxIdle(16);
        pool.setMaxWait(Duration.ofSeconds(2));
        return RedisClient.builder().hostAndPort("127.0.0.1", port).poolConfig(pool)
                .clientConfig(DefaultJedisClientConfig.builder().connectionTimeoutMillis(250).socketTimeoutMillis(1000).build()).build();
    }
    private static String namespace() { return "test-" + UUID.randomUUID(); }
    private static int unusedPort() throws IOException {
        try (var socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
    }
    private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Timed out waiting for local Redis state");
            Thread.sleep(10);
        }
    }
    private static <T> List<T> concurrently(int count, IndexedCall<T> call) throws Exception {
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(executor.submit((Callable<T>) () -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return call.call(index);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var results = new ArrayList<T>();
            for (Future<T> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Concurrent Redis test workers must terminate");
        }
    }
    @FunctionalInterface private interface IndexedCall<T> { T call(int index) throws Exception; }
}
