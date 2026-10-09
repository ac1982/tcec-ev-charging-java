package io.github.ac1982.tcec.client;

import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.model.QueryTokenResponse;
import io.github.ac1982.tcec.model.QueryTokenRequest;
import io.github.ac1982.tcec.protocol.Endpoints;
import io.github.ac1982.tcec.protocol.Endpoint;
import io.github.ac1982.tcec.protocol.RequestValidation;
import io.github.ac1982.tcec.security.AccessToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Opt-in token management for one client, peer, and credential direction. Reuse one session per peer.
 * Token refresh is serialized and coalesced. Business POSTs are never retried, including after token errors.
 * Failed token exchanges have a one-second cooldown to avoid a queued-caller stampede.
 * Early refresh is capped at half the remaining lifetime after the response. This session does not close its client.
 */
public final class TcecSession {
    private final TcecClient client;
    private final Endpoint<QueryTokenRequest, QueryTokenResponse> tokenEndpoint;
    private final Clock clock;
    private final Duration refreshAhead;
    private final ReentrantLock acquisitionLock = new ReentrantLock();
    private AccessToken cached;
    private Instant refreshAt;
    private RuntimeException acquisitionFailure;
    private Instant retryAfter;

    public TcecSession(TcecClient client) { this(client, Clock.systemUTC(), Duration.ofSeconds(30)); }

    /** Explicit token route for peers whose callback server exposes /query_token. */
    public TcecSession(TcecClient client, Endpoint<QueryTokenRequest, QueryTokenResponse> tokenEndpoint) {
        this(client, Clock.systemUTC(), Duration.ofSeconds(30), tokenEndpoint);
    }

    public TcecSession(TcecClient client, Clock clock, Duration refreshAhead) {
        this(client, clock, refreshAhead, Endpoints.QUERY_TOKEN);
    }

    public TcecSession(TcecClient client, Clock clock, Duration refreshAhead,
                       Endpoint<QueryTokenRequest, QueryTokenResponse> tokenEndpoint) {
        this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        if (tokenEndpoint.tokenRequired() || tokenEndpoint.requestType() != QueryTokenRequest.class
                || tokenEndpoint.responseType() != QueryTokenResponse.class)
            throw new IllegalArgumentException("Token endpoint must accept QueryTokenRequest without a bearer token");
        this.client = Objects.requireNonNull(client, "client");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (refreshAhead == null || refreshAhead.isNegative() || refreshAhead.compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalArgumentException("refreshAhead must be from zero to seven days");
        this.refreshAhead = refreshAhead;
    }

    /** Returns a validated cached token or performs one token exchange for concurrent callers. */
    public String accessToken() {
        acquisitionLock.lock();
        try {
            return acquireUnderLock();
        } finally {
            acquisitionLock.unlock();
        }
    }

    private String acquireUnderLock() {
        Instant now = clock.instant();
        if (cached != null && now.isBefore(refreshAt)) return cached.value();
        if (acquisitionFailure != null && now.isBefore(retryAfter)) throw acquisitionFailure;
        try {
            QueryTokenResponse response = client.acquireToken(tokenEndpoint);
            if (response == null || !client.operatorId().equals(response.operatorId()) || !Integer.valueOf(0).equals(response.succStat())
                    || response.accessToken() == null || response.accessToken().length() > 4096
                    || !response.accessToken().matches("[A-Za-z0-9\\-._~+/]+=*")
                    || response.tokenAvailableTime() == null || response.tokenAvailableTime() <= 0
                    || response.tokenAvailableTime() > 604800
                    || (response.failReason() != null && !Integer.valueOf(0).equals(response.failReason()))) {
                throw new ProtocolException(4002, "Token acquisition failed");
            }
            // Start lifetime at request start, conservatively accounting for token exchange latency.
            Instant expires = now.plusSeconds(response.tokenAvailableTime());
            Duration remaining = Duration.between(clock.instant(), expires);
            if (remaining.isNegative() || remaining.isZero()) throw new ProtocolException(4002, "Token acquisition expired");
            cached = new AccessToken(response.accessToken(), expires);
            Duration halfLifetime = remaining.dividedBy(2);
            Duration earlyRefresh = refreshAhead.compareTo(halfLifetime) > 0 ? halfLifetime : refreshAhead;
            refreshAt = expires.minus(earlyRefresh);
            acquisitionFailure = null;
            return cached.value();
        } catch (RuntimeException failure) {
            acquisitionFailure = failure instanceof ProtocolException protocol
                    ? new ProtocolException(protocol.ret(), "Token acquisition failed")
                    : new TransportException("Token acquisition failed");
            retryAfter = clock.instant().plusSeconds(1);
            throw acquisitionFailure;
        }
    }

    public <Q, R> R execute(Endpoint<Q, R> endpoint, Q request) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(request, "request");
        if (!endpoint.requestType().isInstance(request)) throw new IllegalArgumentException("Wrong request type");
        RequestValidation.validate(request);
        if (!endpoint.tokenRequired()) return client.execute(endpoint, request);
        String token = accessToken();
        try {
            return client.execute(endpoint, request, token);
        } catch (ProtocolException failure) {
            if (failure.ret() == 4002) invalidateIfCurrent(token);
            throw failure;
        }
    }

    /** Explicit invalidation. The next protected call will acquire a new token. */
    public void invalidateToken() {
        acquisitionLock.lock();
        try { cached = null; acquisitionFailure = null; }
        finally { acquisitionLock.unlock(); }
    }

    private void invalidateIfCurrent(String token) {
        acquisitionLock.lock();
        try { if (cached != null && cached.value().equals(token)) cached = null; }
        finally { acquisitionLock.unlock(); }
    }

    @Override public String toString() { return "TcecSession[redacted]"; }
}
