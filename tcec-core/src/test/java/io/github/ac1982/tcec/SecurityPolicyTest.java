package io.github.ac1982.tcec;
import io.github.ac1982.tcec.security.*;
import io.github.ac1982.tcec.wire.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class SecurityPolicyTest {
    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-09T04:00:00Z"));
        public ZoneId getZone(){ return ZoneOffset.UTC; } public Clock withZone(ZoneId z){return this;} public Instant instant(){return now.get();}
    }
    private RequestEnvelope envelope(String timestamp,String seq){return new RequestEnvelope("123456789","ignored",timestamp,seq,"ignored");}
    @Test void freshnessAndCalendarValidityRejectInvalidRequests() {
        MutableClock clock=new MutableClock(); var verifier=new RequestVerifier(clock,RequestVerifier.CHINA_ZONE,Duration.ofMinutes(5),new InMemoryReplayStore(clock,100));
        verifier.verify(envelope("20261009120000","0001"));
        for (String ts : new String[]{"20261009115459","20261009120501","20260230120000","20261009240000","2026100912000"})
            assertThrows(ProtocolException.class, () -> verifier.verify(envelope(ts,"0002")));
        assertThrows(ProtocolException.class, () -> verifier.verify(envelope("20261009120000","1")));
    }
    @Test void replayIsAtomicAndCapacityFailsClosed() throws Exception {
        MutableClock clock=new MutableClock(); var store=new InMemoryReplayStore(clock,1);
        try (var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var jobs=new java.util.ArrayList<Future<Boolean>>();
            for(int i=0;i<32;i++) jobs.add(workers.submit(() -> store.claim("123456789","20261009120000","0001",clock.instant().plusSeconds(60))));
            int accepted=0; for(var job:jobs) if(job.get()) accepted++; assertEquals(1,accepted);
        }
        assertFalse(store.claim("123456789","20261009120000","0002",clock.instant().plusSeconds(60)));
        clock.now.set(clock.instant().plusSeconds(61));
        assertTrue(store.claim("123456789","20261009120101","0001",clock.instant().plusSeconds(60)));
    }
    @Test void futureRequestStaysInReplayCacheUntilEntireAcceptanceWindowEnds() {
        MutableClock clock=new MutableClock();var store=new InMemoryReplayStore(clock,100);var verifier=new RequestVerifier(clock,RequestVerifier.CHINA_ZONE,Duration.ofMinutes(5),store);
        var request=envelope("20261009120500","0001");verifier.verify(request);
        clock.now.set(clock.instant().plusSeconds(301));
        assertThrows(ProtocolException.class, () -> verifier.verify(request));
    }
    @Test void tokensExpireReuseAndRemainPartnerScoped() {
        MutableClock clock=new MutableClock();var tokens=new InMemoryTokenStore(clock);
        var first=tokens.issue("123456789",Duration.ofSeconds(60));
        assertTrue(tokens.validate("123456789",first.value())); assertFalse(tokens.validate("987654321",first.value()));
        var second=tokens.issue("123456789",Duration.ofSeconds(60));
        assertEquals(first, second); assertTrue(tokens.validate("123456789",first.value())); assertTrue(tokens.validate("123456789",second.value()));
        assertFalse(tokens.validate("123456789",null));assertFalse(tokens.validate("123456789",""));assertFalse(second.toString().contains(second.value()));
        clock.now.set(second.expiresAt());assertFalse(tokens.validate("123456789",second.value()));
    }
    @Test void tokenLifetimeIsBoundedBySevenDays() {
        var tokens=new InMemoryTokenStore(Clock.systemUTC());
        assertDoesNotThrow(() -> tokens.issue("123456789",Duration.ofDays(7)));
        assertThrows(IllegalArgumentException.class, () -> tokens.issue("123456789",Duration.ofDays(7).plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> tokens.issue("123456789",Duration.ZERO));
    }
}
