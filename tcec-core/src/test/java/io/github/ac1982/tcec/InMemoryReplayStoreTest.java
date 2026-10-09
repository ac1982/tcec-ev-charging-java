package io.github.ac1982.tcec;

import io.github.ac1982.tcec.security.InMemoryReplayStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryReplayStoreTest {
    private static final Instant START = Instant.parse("2026-10-09T08:00:00Z");
    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(START);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @Test void outOfOrderDeadlinesOnlyRemoveExpiredClaims() {
        var clock = new MutableClock();
        var store = new InMemoryReplayStore(clock, 3);
        assertTrue(claim(store, "late", START.plusSeconds(90)));
        assertTrue(claim(store, "early", START.plusSeconds(10)));
        assertTrue(claim(store, "middle", START.plusSeconds(30)));
        clock.now.set(START.plusSeconds(10));
        assertTrue(claim(store, "new", START.plusSeconds(100)));
        assertFalse(claim(store, "early", START.plusSeconds(100)));
        assertFalse(claim(store, "late", START.plusSeconds(200)));
        assertFalse(claim(store, "middle", START.plusSeconds(200)));
        clock.now.set(START.plusSeconds(30));
        assertTrue(claim(store, "middle", START.plusSeconds(200)));
        assertFalse(claim(store, "another", START.plusSeconds(200)));
    }

    @Test void exactExpiryBoundaryAndDuplicateDoNotExtendRetention() {
        var clock = new MutableClock();
        var store = new InMemoryReplayStore(clock, 1);
        Instant expiry = START.plusSeconds(1);
        assertTrue(claim(store, "request", expiry));
        clock.now.set(expiry.minusNanos(1));
        assertFalse(claim(store, "request", Instant.MAX));
        assertFalse(claim(store, "new", Instant.MAX));
        clock.now.set(expiry);
        assertFalse(claim(store, "request", expiry));
        assertTrue(claim(store, "request", Instant.MAX));
        assertFalse(claim(store, "new", Instant.MAX));
    }

    @Test void equalDeadlinesAndInvalidClaimsDoNotPreventCapacityReclamation() {
        var clock = new MutableClock();
        var store = new InMemoryReplayStore(clock, 3);
        for (int i = 0; i < 3; i++) assertTrue(claim(store, "old" + i, START.plusSeconds(1)));
        clock.now.set(START.plusSeconds(1));
        assertFalse(claim(store, "invalid", null));
        assertFalse(claim(store, "invalid", START));
        for (int i = 0; i < 3; i++) assertTrue(claim(store, "new" + i, Instant.MAX));
        assertFalse(claim(store, "overflow", Instant.MAX));
    }

    @Test void replayKeyIncludesPartnerTimestampAndFullFingerprint() {
        var store = new InMemoryReplayStore(Clock.fixed(START, ZoneOffset.UTC), 4);
        assertTrue(store.claim("123456789", "timestamp1", "fingerprint1", Instant.MAX));
        assertTrue(store.claim("987654321", "timestamp1", "fingerprint1", Instant.MAX));
        assertTrue(store.claim("123456789", "timestamp2", "fingerprint1", Instant.MAX));
        assertTrue(store.claim("123456789", "timestamp1", "fingerprint2", Instant.MAX));
        assertFalse(store.claim("123456789", "timestamp1", "fingerprint1", Instant.MAX));
    }

    @Test void clockRollbackDoesNotEvictLiveClaimsOrLeaveOldExpirationEntries() throws Exception {
        var clock = new MutableClock();
        var store = new InMemoryReplayStore(clock, 1);
        assertTrue(claim(store, "request", START.plusSeconds(5)));
        clock.now.set(START.minusSeconds(10));
        assertFalse(claim(store, "request", START.plusSeconds(20)));
        assertFalse(claim(store, "other", START.plusSeconds(20)));
        clock.now.set(START.plusSeconds(5));
        assertTrue(claim(store, "request", START.plusSeconds(20)));
        clock.now.set(START);
        assertFalse(claim(store, "request", START.plusSeconds(30)));
        clock.now.set(START.plusSeconds(5));
        assertFalse(claim(store, "request", START.plusSeconds(30)));
        assertEquals(1, expirationCount(store));
    }

    @Test void refusedAndRepeatedClaimsCannotGrowExpirationQueue() throws Exception {
        var clock = new MutableClock();
        var store = new InMemoryReplayStore(clock, 1);
        for (int cycle = 0; cycle < 200; cycle++) {
            Instant expiry = clock.instant().plusSeconds(1);
            assertTrue(claim(store, "request", expiry));
            for (int attempt = 0; attempt < 20; attempt++) {
                assertFalse(claim(store, "request", Instant.MAX));
                assertFalse(claim(store, "overflow" + attempt, Instant.MAX));
                assertFalse(claim(store, "invalid" + attempt, clock.instant()));
            }
            assertEquals(1, expirationCount(store));
            clock.now.set(expiry);
        }
    }

    @Test void concurrentDuplicateClaimsHaveExactlyOneWinner() throws Exception {
        var store = new InMemoryReplayStore(Clock.fixed(START, ZoneOffset.UTC), 8);
        var start = new CountDownLatch(1);
        var jobs = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 100; i++) jobs.add(() -> { start.await(); return claim(store, "same", Instant.MAX); });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = jobs.stream().map(executor::submit).toList();
            start.countDown();
            int accepted = 0;
            for (var result : results) if (result.get()) accepted++;
            assertEquals(1, accepted);
        }
        assertEquals(1, expirationCount(store));
    }

    @Test void concurrentDistinctClaimsNeverExceedCapacity() throws Exception {
        var store = new InMemoryReplayStore(Clock.fixed(START, ZoneOffset.UTC), 16);
        var start = new CountDownLatch(1);
        var jobs = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 100; i++) {
            String fingerprint = "request" + i;
            jobs.add(() -> { start.await(); return claim(store, fingerprint, Instant.MAX); });
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = jobs.stream().map(executor::submit).toList();
            start.countDown();
            int accepted = 0;
            for (var result : results) if (result.get()) accepted++;
            assertEquals(16, accepted);
        }
        assertEquals(16, expirationCount(store));
    }

    @Test void randomizedClaimsMatchFullScanReferenceIncludingClockJumps() throws Exception {
        var clock = new MutableClock();
        int capacity = 17;
        var store = new InMemoryReplayStore(clock, capacity);
        Map<String, Instant> reference = new HashMap<>();
        var random = new Random(923541);
        for (int i = 0; i < 5_000; i++) {
            Instant now = START.plusSeconds(random.nextInt(100));
            clock.now.set(now);
            reference.values().removeIf(expiry -> !expiry.isAfter(now));
            String key = "request" + random.nextInt(40);
            Instant expiry = START.plusSeconds(random.nextInt(120));
            boolean expected = expiry.isAfter(now) && !reference.containsKey(key) && reference.size() < capacity;
            if (expected) reference.put(key, expiry);
            assertEquals(expected, claim(store, key, expiry), "operation " + i);
            assertEquals(reference.size(), expirationCount(store), "expiration entries at operation " + i);
        }
    }

    @Test void invalidConfigurationIsRejected() {
        assertThrows(NullPointerException.class, () -> new InMemoryReplayStore(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryReplayStore(Clock.systemUTC(), 0));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryReplayStore(Clock.systemUTC(), -1));
    }

    private static boolean claim(InMemoryReplayStore store, String fingerprint, Instant expiry) {
        return store.claim("123456789", "timestamp", fingerprint, expiry);
    }

    private static int expirationCount(InMemoryReplayStore store) throws Exception {
        var field = InMemoryReplayStore.class.getDeclaredField("expirations");
        field.setAccessible(true);
        return ((Collection<?>) field.get(store)).size();
    }
}
