package io.github.ac1982.tcec;

import io.github.ac1982.tcec.security.AccessToken;
import io.github.ac1982.tcec.security.InMemoryTokenStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryTokenStoreTest {
    private static final String ID = "123456789";
    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-09T08:00:00Z"));
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @Test void concurrentIssueReusesOneLiveTokenWithoutExtendingExpiry() throws Exception {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 1);
        var jobs = new ArrayList<Callable<AccessToken>>();
        for (int i = 0; i < 100; i++) jobs.add(() -> store.issue(ID, Duration.ofSeconds(60)));
        AccessToken first;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executor.invokeAll(jobs);
            first = results.getFirst().get();
            for (var result : results) assertEquals(first, result.get());
        }
        clock.now.set(clock.instant().plusSeconds(30));
        assertEquals(first, store.issue(ID, Duration.ofDays(7)));
        assertEquals(Instant.parse("2026-10-09T08:01:00Z"), first.expiresAt());
        clock.now.set(first.expiresAt());
        assertFalse(store.validate(ID, first.value()));
        assertNotEquals(first.value(), store.issue(ID, Duration.ofSeconds(60)).value());
    }

    @Test void capacityNeverEvictsALivePartnerAndExpiredEntriesFreeSpace() {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 1);
        AccessToken token = store.issue(ID, Duration.ofSeconds(60));
        assertEquals(-1, assertThrows(ProtocolException.class,
                () -> store.issue("987654321", Duration.ofSeconds(60))).ret());
        assertTrue(store.validate(ID, token.value()));
        clock.now.set(token.expiresAt());
        AccessToken other = store.issue("987654321", Duration.ofSeconds(60));
        assertTrue(store.validate("987654321", other.value()));
        assertFalse(store.validate(ID, other.value()));
    }

    @Test void invalidConfigurationAndArgumentsAreRejected() {
        var clock = new MutableClock();
        assertThrows(IllegalArgumentException.class, () -> new InMemoryTokenStore(clock, 0));
        var store = new InMemoryTokenStore(clock, 1);
        for (String id : new String[]{null, "", "123", "12345678/"})
            assertThrows(IllegalArgumentException.class, () -> store.issue(id, Duration.ofSeconds(1)));
        for (Duration ttl : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(1500), Duration.ofDays(8)})
            assertThrows(IllegalArgumentException.class, () -> store.issue(ID, ttl));
        AccessToken token = store.issue(ID, Duration.ofSeconds(1));
        assertFalse(store.validate(ID, "x".repeat(4097)));
        assertFalse(token.toString().contains(token.value()));
    }

    @Test void outOfOrderExpirationsFreeOnlyExpiredPartners() {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 3);
        AccessToken late = store.issue(ID, Duration.ofSeconds(90));
        AccessToken early = store.issue("222222222", Duration.ofSeconds(10));
        AccessToken middle = store.issue("333333333", Duration.ofSeconds(30));
        clock.now.set(early.expiresAt());
        AccessToken newcomer = store.issue("444444444", Duration.ofSeconds(100));
        assertTrue(store.validate(ID, late.value()));
        assertTrue(store.validate("333333333", middle.value()));
        assertTrue(store.validate("444444444", newcomer.value()));
        assertFalse(store.validate("222222222", early.value()));
        assertThrows(ProtocolException.class, () -> store.issue("555555555", Duration.ofSeconds(1)));
        clock.now.set(middle.expiresAt());
        assertDoesNotThrow(() -> store.issue("555555555", Duration.ofSeconds(1)));
        assertTrue(store.validate(ID, late.value()));
        assertTrue(store.validate("444444444", newcomer.value()));
    }

    @Test void nanosecondBeforeExpiryIsLiveAndExactExpiryRequiresReplacement() {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 1);
        AccessToken token = store.issue(ID, Duration.ofSeconds(1));
        clock.now.set(token.expiresAt().minusNanos(1));
        assertTrue(store.validate(ID, token.value()));
        assertSame(token, store.issue(ID, Duration.ofDays(7)));
        clock.now.set(token.expiresAt());
        AccessToken replacement = store.issue(ID, Duration.ofSeconds(1));
        assertNotEquals(token.value(), replacement.value());
        assertFalse(store.validate(ID, token.value()));
        assertTrue(store.validate(ID, replacement.value()));
    }

    @Test void liveReuseDoesNotRequireGlobalCleanupOrEnqueueAnotherExpiration() throws Exception {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 2);
        AccessToken live = store.issue(ID, Duration.ofSeconds(60));
        AccessToken expired = store.issue("222222222", Duration.ofSeconds(1));
        clock.now.set(expired.expiresAt());
        for (int i = 0; i < 100; i++) assertSame(live, store.issue(ID, Duration.ofDays(7)));
        assertEquals(2, expirationCount(store));
        AccessToken newcomer = store.issue("333333333", Duration.ofSeconds(10));
        assertEquals(2, expirationCount(store));
        assertTrue(store.validate(ID, live.value()));
        assertTrue(store.validate("333333333", newcomer.value()));
    }

    @Test void expiredValidationDrainsDueEntriesAndNeverLeavesStaleTokens() throws Exception {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 3);
        AccessToken first = store.issue(ID, Duration.ofSeconds(2));
        AccessToken second = store.issue("222222222", Duration.ofSeconds(1));
        AccessToken live = store.issue("333333333", Duration.ofSeconds(60));
        clock.now.set(first.expiresAt());
        assertFalse(store.validate(ID, first.value()));
        assertFalse(store.validate("222222222", second.value()));
        assertEquals(1, expirationCount(store));
        assertTrue(store.validate("333333333", live.value()));
        assertDoesNotThrow(() -> store.issue(ID, Duration.ofSeconds(3)));
        assertDoesNotThrow(() -> store.issue("222222222", Duration.ofSeconds(3)));
        assertEquals(3, expirationCount(store));
    }

    @Test void clockRollbackAfterExpiredValidationCannotReviveOrDeleteReplacement() throws Exception {
        var clock = new MutableClock();
        Instant start = clock.instant();
        var store = new InMemoryTokenStore(clock, 1);
        AccessToken old = store.issue(ID, Duration.ofSeconds(5));
        clock.now.set(old.expiresAt());
        assertFalse(store.validate(ID, old.value()));
        assertEquals(0, expirationCount(store));
        clock.now.set(start);
        assertFalse(store.validate(ID, old.value()));
        AccessToken replacement = store.issue(ID, Duration.ofSeconds(20));
        assertEquals(1, expirationCount(store));
        clock.now.set(old.expiresAt());
        assertThrows(ProtocolException.class, () -> store.issue("222222222", Duration.ofSeconds(1)));
        assertSame(replacement, store.issue(ID, Duration.ofSeconds(1)));
        assertTrue(store.validate(ID, replacement.value()));
        assertFalse(store.validate(ID, old.value()));
        assertEquals(1, expirationCount(store));
    }

    @Test void reissueValidationAndRefusalsKeepExpirationQueueBounded() throws Exception {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 1);
        Instant start = clock.instant();
        for (int i = 0; i < 200; i++) {
            // Repeated rollback exposes lazy tombstones that might otherwise appear harmless.
            clock.now.set(start);
            AccessToken token = store.issue(ID, Duration.ofSeconds(i + 1));
            assertSame(token, store.issue(ID, Duration.ofDays(7)));
            assertThrows(ProtocolException.class, () -> store.issue("222222222", Duration.ofSeconds(1)));
            assertEquals(1, expirationCount(store));
            clock.now.set(token.expiresAt());
            assertFalse(store.validate(ID, token.value()));
            assertEquals(0, expirationCount(store));
        }
    }

    @Test void concurrentPartnersNeverExceedCapacityOrLoseAdmittedTokens() throws Exception {
        var clock = new MutableClock();
        var store = new InMemoryTokenStore(clock, 16);
        var start = new CountDownLatch(1);
        var jobs = new ArrayList<Callable<AccessToken>>();
        for (int i = 0; i < 100; i++) {
            String partner = Integer.toString(100_000_000 + i);
            jobs.add(() -> {
                start.await();
                try { return store.issue(partner, Duration.ofSeconds(60)); }
                catch (ProtocolException e) { assertEquals(-1, e.ret()); return null; }
            });
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = jobs.stream().map(executor::submit).toList();
            start.countDown();
            int accepted = 0;
            for (int i = 0; i < results.size(); i++) {
                AccessToken token = results.get(i).get();
                if (token != null) {
                    accepted++;
                    assertTrue(store.validate(Integer.toString(100_000_000 + i), token.value()));
                }
            }
            assertEquals(16, accepted);
        }
        assertEquals(16, expirationCount(store));
    }

    private static int expirationCount(InMemoryTokenStore store) throws Exception {
        var field = InMemoryTokenStore.class.getDeclaredField("expirations");
        field.setAccessible(true);
        return ((Collection<?>) field.get(store)).size();
    }
}
