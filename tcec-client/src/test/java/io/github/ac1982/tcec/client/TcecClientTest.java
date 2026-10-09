package io.github.ac1982.tcec.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.codec.TcecCodec;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.protocol.Endpoint;
import io.github.ac1982.tcec.protocol.Endpoints;
import io.github.ac1982.tcec.model.QueryTokenResponse;
import io.github.ac1982.tcec.security.PartnerCredentials;
import io.github.ac1982.tcec.wire.RequestEnvelope;
import io.github.ac1982.tcec.wire.ResponseEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class TcecClientTest {
    static final PartnerCredentials CREDENTIALS = new PartnerCredentials("123456789", "operator-secret",
            "1234567890abcdef", "1234567890abcdef", "1234567890abcdef");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneId.of("UTC"));
    static final Endpoint<Payload, Payload> ENDPOINT = new Endpoint<>("notification_stationStatus", Payload.class, Payload.class, true, "/notification_stationStatus");
    final TcecCodec codec = new TcecCodec(CREDENTIALS);
    final AtomicInteger received = new AtomicInteger();
    final List<RequestEnvelope> envelopes = Collections.synchronizedList(new ArrayList<>());
    volatile Function<HttpExchange, Reply> responder;
    HttpServer server;
    ExecutorService executor;
    URI uri;

    record Payload(@JsonProperty("Message") String message) {}
    record Reply(int status, byte[] body) {}

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        responder = exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("/notification_stationStatus", exchange.getRequestURI().getPath());
            assertEquals("Bearer public-token", exchange.getRequestHeaders().getFirst("Authorization"));
            RequestEnvelope envelope;
            try {
                envelope = WireJson.read(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), RequestEnvelope.class);
            } catch (IOException e) { throw new AssertionError(e); }
            envelopes.add(envelope);
            Payload payload = codec.decodeRequest(envelope, Payload.class);
            return json(codec.encodeResponse(payload));
        };
        server.createContext("/", exchange -> {
            received.incrementAndGet();
            Reply reply = responder.apply(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), reply.body().length);
            try (var body = exchange.getResponseBody()) { body.write(reply.body()); }
        });
        server.start();
        uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/evcs/v1");
    }

    @AfterEach void stop() {
        server.stop(0);
        executor.close();
    }

    TcecClient client() { return new TcecClient(uri, CREDENTIALS, options(20000, 65536), CLOCK); }
    static ClientOptions options(long millis, int maxBytes) {
        return new ClientOptions(Duration.ofSeconds(3), Duration.ofMillis(millis), maxBytes, ZoneId.of("Asia/Shanghai"), true);
    }
    Reply json(Object value) { return new Reply(200, WireJson.write(value).getBytes(StandardCharsets.UTF_8)); }

    @Test void encryptedHttpRoundTripAndSequence() {
        try (TcecClient client = client()) {
            assertEquals(new Payload("中文 ⚡"), client.execute(ENDPOINT, new Payload("中文 ⚡"), "public-token"));
            client.execute(ENDPOINT, new Payload("second"), "public-token");
        }
        assertEquals(2, received.get());
        assertEquals("20261009160000", envelopes.getFirst().timeStamp());
        assertEquals("0001", envelopes.getFirst().seq());
        assertEquals("0002", envelopes.getLast().seq());
    }

    @Test void tokenQueryHasNoAuthorizationHeader() {
        responder = exchange -> {
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            return json(codec.encodeResponse(new Payload("token response")));
        };
        Endpoint<Payload, Payload> tokenEndpoint = new Endpoint<>("query_token", Payload.class, Payload.class, false);
        try (TcecClient client = client()) {
            assertEquals("token response", client.execute(tokenEndpoint, new Payload("request")).message());
        }
    }

    @Test void checksMacBeforeTrustingResponseReturnCode() {
        ResponseEnvelope signed = codec.encodeError(4002, "Invalid access token");
        responder = exchange -> json(new ResponseEnvelope(500, signed.msg(), signed.data(), signed.sig()));
        try (TcecClient client = client()) {
            ProtocolException exception = assertThrows(ProtocolException.class,
                    () -> client.execute(ENDPOINT, new Payload("x"), "public-token"));
            assertEquals(4001, exception.ret());
        }
    }

    @Test void signedErrorIsReportedWithoutTrustingRemoteMessage() {
        responder = exchange -> json(codec.encodeError(4002, "SECRET remote details"));
        try (TcecClient client = client()) {
            ProtocolException exception = assertThrows(ProtocolException.class,
                    () -> client.execute(ENDPOINT, new Payload("x"), "public-token"));
            assertEquals(4002, exception.ret());
            assertFalse(exception.toString().contains("SECRET"));
        }
    }

    @Test void noImplicitRetryForFailedBusinessPost() {
        responder = exchange -> new Reply(503, "secret body".getBytes(StandardCharsets.UTF_8));
        try (TcecClient client = client()) {
            TransportException exception = assertThrows(TransportException.class,
                    () -> client.execute(ENDPOINT, new Payload("charge"), "public-token"));
            assertEquals(503, exception.statusCode());
            assertFalse(exception.toString().contains("secret body"));
        }
        assertEquals(1, received.get());
    }

    @Test void redirectIsNotFollowed() {
        responder = exchange -> {
            exchange.getResponseHeaders().set("Location", uri.resolve("/redirected").toString());
            return new Reply(302, new byte[]{'x'});
        };
        try (TcecClient client = client()) {
            assertEquals(302, assertThrows(TransportException.class,
                    () -> client.execute(ENDPOINT, new Payload("x"), "public-token")).statusCode());
        }
        assertEquals(1, received.get());
    }

    @Test void boundsResponseBody() {
        responder = exchange -> new Reply(200, new byte[5000]);
        try (TcecClient client = new TcecClient(uri, CREDENTIALS, options(20000, 512), CLOCK)) {
            assertThrows(TransportException.class, () -> client.execute(ENDPOINT, new Payload("x"), "public-token"));
        }
        assertEquals(1, received.get());
    }

    @Test void timeoutDoesNotRetryPost() {
        responder = exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return json(codec.encodeResponse(new Payload("late")));
        };
        try (TcecClient client = new TcecClient(uri, CREDENTIALS, options(75, 65536), CLOCK)) {
            assertThrows(TransportException.class, () -> client.execute(ENDPOINT, new Payload("x"), "public-token"));
        }
        assertEquals(1, received.get());
    }

    @Test void completeBodyHasDeadlineEvenAfterHeadersArrive() throws Exception {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            received.incrementAndGet();
            byte[] body = WireJson.write(codec.encodeResponse(new Payload("late body"))).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body, 0, 1);
                output.flush();
                try { Thread.sleep(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                output.write(body, 1, body.length - 1);
            }
        });
        long start = System.nanoTime();
        try (TcecClient client = new TcecClient(uri, CREDENTIALS, options(150, 65536), CLOCK)) {
            assertThrows(TransportException.class, () -> client.execute(ENDPOINT, new Payload("x"), "public-token"));
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 900, "Must not wait for the body after the deadline");
        assertEquals(1, received.get());
    }

    @Test void malformedResponseIsGenericFailure() {
        responder = exchange -> new Reply(200, "SECRET not JSON".getBytes(StandardCharsets.UTF_8));
        try (TcecClient client = client()) {
            TransportException error = assertThrows(TransportException.class,
                    () -> client.execute(ENDPOINT, new Payload("x"), "public-token"));
            assertEquals("Malformed protocol response", error.getMessage());
        }
    }

    @Test void concurrentRequestsHaveUniqueSequences() throws Exception {
        try (TcecClient client = client(); ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Callable<Payload>> jobs = new ArrayList<>();
            for (int i = 0; i < 40; i++) jobs.add(() -> client.execute(ENDPOINT, new Payload("x"), "public-token"));
            for (var future : callers.invokeAll(jobs)) assertEquals("x", future.get().message());
        }
        assertEquals(40, envelopes.stream().map(RequestEnvelope::seq).distinct().count());
    }

    @Test void productionRequiresHttpsAndLocalExceptionIsNarrow() {
        assertThrows(IllegalArgumentException.class, () -> new TcecClient(uri, CREDENTIALS));
        for (String invalid : List.of("http://example.org/", "https://user:secret@example.org/", "https://example.org/?x=1", "https://example.org/#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> new TcecClient(URI.create(invalid), CREDENTIALS, options(100, 1024), CLOCK));
        }
    }

    @Test void sessionCoalescesConcurrentAcquisitionAndRefreshesAfterExpiry() throws Exception {
        AtomicInteger issued = new AtomicInteger();
        responder = exchange -> json(codec.encodeResponse(new QueryTokenResponse("123456789", 0,
                "token-" + issued.incrementAndGet(), 60, 0)));
        MutableClock clock = new MutableClock(CLOCK.instant());
        try (TcecClient client = client(); ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            TcecSession session = new TcecSession(client, clock, Duration.ZERO);
            List<java.util.concurrent.Callable<String>> jobs = new ArrayList<>();
            for (int i = 0; i < 40; i++) jobs.add(session::accessToken);
            for (var future : callers.invokeAll(jobs)) assertEquals("token-1", future.get());
            assertEquals(1, issued.get());
            clock.advance(Duration.ofSeconds(60));
            for (var future : callers.invokeAll(jobs)) assertEquals("token-2", future.get());
            assertEquals(2, issued.get());
            session.invalidateToken();
            assertEquals("token-3", session.accessToken());
            assertFalse(session.toString().contains("token-3"));
        }
    }

    @Test void sessionNeverRetriesBusinessPostAfterTokenError() {
        AtomicInteger issued = new AtomicInteger();
        responder = exchange -> exchange.getRequestURI().getPath().endsWith("query_token")
                ? json(codec.encodeResponse(new QueryTokenResponse("123456789", 0, "token-" + issued.incrementAndGet(), 60, 0)))
                : json(codec.encodeError(4002, "Token expired"));
        try (TcecClient client = client()) {
            TcecSession session = new TcecSession(client, CLOCK, Duration.ZERO);
            assertEquals(4002, assertThrows(ProtocolException.class, () -> session.execute(ENDPOINT, new Payload("charge"))).ret());
            assertEquals(2, received.get()); // One token exchange, one business POST.
            assertEquals(1, issued.get());
            assertEquals("token-2", session.accessToken()); // New explicit operation may reacquire.
        }
    }

    @Test void sessionRejectsMismatchedIdentityAndBusinessFailureWithoutCaching() {
        AtomicInteger attempt = new AtomicInteger();
        responder = exchange -> json(codec.encodeResponse(attempt.incrementAndGet() == 1
                ? new QueryTokenResponse("987654321", 0, "foreign-token", 60, 0)
                : new QueryTokenResponse("123456789", 1, "", 0, 2)));
        try (TcecClient client = client()) {
            MutableClock clock = new MutableClock(CLOCK.instant());
            TcecSession session = new TcecSession(client, clock, Duration.ZERO);
            assertEquals(4002, assertThrows(ProtocolException.class, session::accessToken).ret());
            clock.advance(Duration.ofSeconds(1));
            assertEquals(4002, assertThrows(ProtocolException.class, session::accessToken).ret());
            assertEquals(2, received.get());
        }
    }

    @Test void failedConcurrentTokenExchangeHasBoundedCooldown() throws Exception {
        responder = exchange -> json(codec.encodeResponse(new QueryTokenResponse("123456789", 1, "", 0, 2)));
        try (TcecClient client = client(); ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            MutableClock clock = new MutableClock(CLOCK.instant());
            TcecSession session = new TcecSession(client, clock, Duration.ZERO);
            List<java.util.concurrent.Callable<Integer>> jobs = new ArrayList<>();
            for (int i = 0; i < 40; i++) jobs.add(() -> assertThrows(ProtocolException.class, session::accessToken).ret());
            for (var future : callers.invokeAll(jobs)) assertEquals(4002, future.get());
            assertEquals(1, received.get());
            clock.advance(Duration.ofSeconds(1));
            assertThrows(ProtocolException.class, session::accessToken);
            assertEquals(2, received.get());
        }
    }

    @Test void tokenCachesAreIsolatedByClientAndCredentialDirection() {
        PartnerCredentials other = new PartnerCredentials("987654321", "other-secret",
                "1234567890abcdef", "1234567890abcdef", "fedcba0987654321");
        TcecCodec otherCodec = new TcecCodec(other);
        responder = exchange -> {
            RequestEnvelope request;
            try { request = WireJson.read(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), RequestEnvelope.class); }
            catch (IOException e) { throw new AssertionError(e); }
            boolean secondPeer = request.operatorId().equals(other.operatorId());
            String id = secondPeer ? other.operatorId() : CREDENTIALS.operatorId();
            return json((secondPeer ? otherCodec : codec).encodeResponse(new QueryTokenResponse(id, 0, "token-" + id, 60, 0)));
        };
        try (TcecClient first = client(); TcecClient second = new TcecClient(uri.resolve("/other/"), other, options(20000, 65536), CLOCK)) {
            TcecSession a = new TcecSession(first, CLOCK, Duration.ZERO);
            TcecSession b = new TcecSession(second, CLOCK, Duration.ZERO);
            assertEquals("token-123456789", a.accessToken());
            assertEquals("token-987654321", b.accessToken());
            assertEquals("token-123456789", a.accessToken());
            assertEquals(2, received.get());
        }
    }

    @Test void shortLivedTokenStillCoalescesWithDefaultEarlyRefresh() {
        responder = exchange -> json(codec.encodeResponse(new QueryTokenResponse("123456789", 0, "short-token", 1, 0)));
        try (TcecClient client = client()) {
            TcecSession session = new TcecSession(client, CLOCK, Duration.ofSeconds(30));
            assertEquals("short-token", session.accessToken());
            assertEquals("short-token", session.accessToken());
            assertEquals(1, received.get());
        }
    }

    @Test void earlyRefreshUsesRemainingLifetimeAfterSlowTokenExchange() {
        MutableClock clock = new MutableClock(CLOCK.instant());
        responder = exchange -> {
            clock.advance(Duration.ofSeconds(6));
            return json(codec.encodeResponse(new QueryTokenResponse("123456789", 0, "slow-token", 10, 0)));
        };
        try (TcecClient client = client()) {
            TcecSession session = new TcecSession(client, clock, Duration.ofSeconds(30));
            assertEquals("slow-token", session.accessToken());
            assertEquals("slow-token", session.accessToken());
            assertEquals(1, received.get());
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        synchronized void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public synchronized Instant instant() { return instant; }
    }

    @Test void missingOrHeaderInjectedTokenFailsBeforeNetwork() {
        try (TcecClient client = client()) {
            assertThrows(IllegalArgumentException.class, () -> client.execute(ENDPOINT, new Payload("x")));
            assertThrows(IllegalArgumentException.class, () -> client.execute(ENDPOINT, new Payload("x"), "x\r\nHeader: exploit"));
            assertThrows(IllegalArgumentException.class, () -> client.execute(ENDPOINT, new Payload("x"), "x".repeat(4097)));
        }
        assertEquals(0, received.get());
    }

    @Test void everyBuiltInUsesItsCanonicalAbsolutePathAndAuthorizationBoundary() {
        List<String> paths = Collections.synchronizedList(new ArrayList<>());
        responder = exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            boolean token = exchange.getRequestURI().getPath().equals("/evcs/sdk/query_token");
            assertEquals(token ? null : "Bearer public-token", exchange.getRequestHeaders().getFirst("Authorization"));
            return json(codec.encodeResponse(new Payload("ok")));
        };
        try (TcecClient client = new TcecClient(uri.resolve("/ignored/base/"), CREDENTIALS, options(20000, 65536), CLOCK)) {
            for (Endpoint<?, ?> endpoint : Endpoints.all()) {
                Endpoint<Payload, Payload> transport = new Endpoint<>(endpoint.path(), Payload.class, Payload.class,
                        endpoint.tokenRequired(), endpoint.outboundPath());
                assertEquals("ok", client.execute(transport, new Payload("request"),
                        endpoint.tokenRequired() ? "public-token" : null).message());
            }
        }
        assertEquals(29, paths.size());
        assertEquals(Endpoints.all().stream().map(Endpoint::outboundPath).toList(), paths);
        assertEquals(29, paths.stream().distinct().count());
        assertTrue(paths.contains("/notification_stationStatus"));
        assertEquals(1, Collections.frequency(paths, "/notification_start_charge_result"));
    }

    @Test void relativeCustomEndpointUsesExplicitBaseDirectory() {
        responder = exchange -> {
            assertEquals("/custom/ping", exchange.getRequestURI().getPath());
            return json(codec.encodeResponse(new Payload("ok")));
        };
        try (TcecClient client = new TcecClient(uri.resolve("/custom/"), CREDENTIALS, options(20000, 65536), CLOCK)) {
            assertEquals("ok", client.execute(new Endpoint<>("ping", Payload.class, Payload.class, true),
                    new Payload("request"), "public-token").message());
        }
    }

    @Test void sessionRejectsIncompleteTokenWithoutSendingBusinessTraffic() {
        responder = exchange -> json(codec.encodeResponse(new QueryTokenResponse("123456789", null, "token", 60, null)));
        try (TcecClient client = client()) {
            var session = new TcecSession(client, CLOCK, Duration.ZERO);
            assertEquals(4002, assertThrows(ProtocolException.class, () -> session.execute(ENDPOINT, new Payload("request"))).ret());
            assertEquals(1, received.get());
        }
    }


    @Test void callbackPeerCanExplicitlySelectRootTokenExchange() {
        AtomicInteger tokenCalls = new AtomicInteger();
        responder = exchange -> {
            if (exchange.getRequestURI().getPath().equals("/query_token")) {
                tokenCalls.incrementAndGet();
                assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
                return json(codec.encodeResponse(new QueryTokenResponse("123456789", 0, "callback-token", 60, 0)));
            }
            assertEquals("/notification_stationStatus", exchange.getRequestURI().getPath());
            assertEquals("Bearer callback-token", exchange.getRequestHeaders().getFirst("Authorization"));
            return json(codec.encodeResponse(new Payload("accepted")));
        };
        var tokenEndpoint = new Endpoint<>("query_token", io.github.ac1982.tcec.model.QueryTokenRequest.class,
                QueryTokenResponse.class, false, "/query_token");
        try (TcecClient client = client()) {
            var session = new TcecSession(client, CLOCK, Duration.ZERO, tokenEndpoint);
            assertEquals("accepted", session.execute(ENDPOINT, new Payload("callback")).message());
            assertEquals(1, tokenCalls.get());
            assertEquals(2, received.get());
            assertThrows(IllegalArgumentException.class, () -> new TcecSession(client, CLOCK, Duration.ZERO,
                    new Endpoint<>("query_token", io.github.ac1982.tcec.model.QueryTokenRequest.class,
                            QueryTokenResponse.class, true, "/query_token")));
        }
    }

    @Test void mismatchedOuterResponseIdentityCannotPopulateSessionCache() {
        responder = exchange -> {
            ResponseEnvelope valid = codec.encodeResponse(new QueryTokenResponse("123456789", 0, "token", 60, 0));
            return json(new ResponseEnvelope(valid.ret(), valid.msg(), valid.data(), valid.sig(), "987654321"));
        };
        try (TcecClient client = client()) {
            var session = new TcecSession(client, CLOCK, Duration.ZERO);
            assertEquals(4001, assertThrows(ProtocolException.class,
                    () -> session.execute(ENDPOINT, new Payload("request"))).ret());
            assertEquals(1, received.get());
        }
    }

    @Test void requiredRequestFieldsAreCheckedBeforeNetwork() {
        try (TcecClient client = client()) {
            assertEquals(4004, assertThrows(ProtocolException.class, () -> client.execute(Endpoints.QUERY_GROUND_LOCKS,
                    new io.github.ac1982.tcec.model.QueryGroundLocksRequest(null), "public-token")).ret());
            var session = new TcecSession(client, CLOCK, Duration.ZERO);
            assertEquals(4004, assertThrows(ProtocolException.class, () -> session.execute(Endpoints.QUERY_GROUND_LOCKS,
                    new io.github.ac1982.tcec.model.QueryGroundLocksRequest(null))).ret());
            assertEquals(0, received.get());
        }
    }


    @Test void lateFailureForAnOldTokenDoesNotInvalidateAConcurrentReplacement() throws Exception {
        var businessStarted = new java.util.concurrent.CountDownLatch(1);
        var finishBusiness = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger issued = new AtomicInteger();
        responder = exchange -> {
            if (exchange.getRequestURI().getPath().equals("/evcs/sdk/query_token"))
                return json(codec.encodeResponse(new QueryTokenResponse("123456789", 0, "token-" + issued.incrementAndGet(), 60, 0)));
            assertEquals("Bearer token-1", exchange.getRequestHeaders().getFirst("Authorization"));
            businessStarted.countDown();
            try { assertTrue(finishBusiness.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            return json(codec.encodeError(4002, "Expired"));
        };
        try (TcecClient client = client(); var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var session = new TcecSession(client, CLOCK, Duration.ZERO);
            var oldBusiness = callers.submit(() -> assertThrows(ProtocolException.class,
                    () -> session.execute(ENDPOINT, new Payload("business"))).ret());
            try {
                assertTrue(businessStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));
                session.invalidateToken();
                assertEquals("token-2", session.accessToken());
            } finally { finishBusiness.countDown(); }
            assertEquals(4002, oldBusiness.get());
            assertEquals("token-2", session.accessToken());
            assertEquals(2, issued.get());
            assertEquals(3, received.get());
        }
    }


    @Test void authenticatedSuccessfulTokenMayOmitOptionalFailureReason() {
        responder = exchange -> json(codec.encodeResponse(new QueryTokenResponse("123456789", 0, "valid-token", 60, null)));
        try (TcecClient client = client()) {
            var session = new TcecSession(client, CLOCK, Duration.ZERO);
            assertEquals("valid-token", session.accessToken());
            assertEquals("valid-token", session.accessToken());
            assertEquals(1, received.get());
        }
    }

    @Test void authenticatedTokenWithNonzeroFailureReasonIsRejected() {
        responder = exchange -> json(codec.encodeResponse(new QueryTokenResponse("123456789", 0, "invalid-token", 60, 2)));
        try (TcecClient client = client()) {
            var session = new TcecSession(client, CLOCK, Duration.ZERO);
            assertEquals(4002, assertThrows(ProtocolException.class, session::accessToken).ret());
            assertEquals(1, received.get());
        }
    }

}
