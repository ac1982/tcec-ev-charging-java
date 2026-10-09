package io.github.ac1982.tcec.client;

import io.github.ac1982.tcec.codec.TcecCodec;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.protocol.Endpoint;
import io.github.ac1982.tcec.protocol.RequestValidation;
import io.github.ac1982.tcec.model.QueryTokenRequest;
import io.github.ac1982.tcec.model.QueryTokenResponse;
import io.github.ac1982.tcec.security.PartnerCredentials;
import io.github.ac1982.tcec.wire.RequestEnvelope;
import io.github.ac1982.tcec.wire.ResponseEnvelope;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Typed synchronous CEC client. Each invocation sends exactly one application-level POST.
 * Tokens are supplied explicitly; TcecSession optionally manages acquisition. Business POSTs are never replayed.
 * Built-in endpoints use canonical absolute wire paths on the configured origin; custom relative
 * endpoints resolve against the configured base URI path.
 * Redirects are never followed. Close this client when it is no longer needed.
 */
public final class TcecClient implements AutoCloseable {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final URI baseUri;
    private final ClientOptions options;
    private final Clock clock;
    private final TcecCodec codec;
    private final PartnerCredentials credentials;
    private final HttpClient http;
    private String lastTimestamp;
    private int sequence;

    public TcecClient(URI baseUri, PartnerCredentials credentials) {
        this(baseUri, credentials, ClientOptions.defaults(), Clock.systemUTC());
    }

    public TcecClient(URI baseUri, PartnerCredentials credentials, ClientOptions options, Clock clock) {
        this.options = Objects.requireNonNull(options, "options");
        this.baseUri = validateBaseUri(baseUri, options.allowHttpLoopback());
        this.clock = Objects.requireNonNull(clock, "clock");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.codec = new TcecCodec(credentials);
        this.http = HttpClient.newBuilder().connectTimeout(options.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public <Q, R> R execute(Endpoint<Q, R> endpoint, Q request, String accessToken) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(request, "request");
        if (!endpoint.requestType().isInstance(request)) throw new IllegalArgumentException("Wrong request type");
        if (endpoint.tokenRequired() && (accessToken == null || accessToken.isBlank())) {
            throw new IllegalArgumentException("An access token is required");
        }
        if (accessToken != null && (accessToken.length() > 4096 || !accessToken.matches("[A-Za-z0-9\\-._~+/]+=*"))) {
            throw new IllegalArgumentException("Invalid access token format");
        }
        RequestValidation.validate(request);
        RequestEnvelope envelope = createRequest(request);
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(endpoint.outboundPath()))
                .timeout(options.requestTimeout()).header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(WireJson.write(envelope), StandardCharsets.UTF_8));
        if (endpoint.tokenRequired()) builder.header("Authorization", "Bearer " + accessToken);
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(builder.build(),
                ignored -> new LimitedBodySubscriber(options.maxResponseBytes()));
        try {
            // HttpRequest.timeout alone can expire after headers. Bound complete body reception as well.
            HttpResponse<byte[]> response = pending.get(options.requestTimeout().toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) {
                throw new TransportException("Unexpected HTTP status", response.statusCode());
            }
            ResponseEnvelope wire;
            try {
                wire = WireJson.read(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(response.body())).toString(), ResponseEnvelope.class);
            } catch (RuntimeException | java.nio.charset.CharacterCodingException malformed) {
                throw new TransportException("Malformed protocol response");
            }
            // MAC verification is performed by decodeResponse before errors or decrypted data are trusted.
            return codec.decodeResponse(wire, endpoint.responseType());
        } catch (TimeoutException timeout) {
            pending.cancel(true);
            throw new TransportException("HTTP request timed out");
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new TransportException("HTTP request interrupted");
        } catch (ExecutionException failure) {
            throw new TransportException("HTTP request failed");
        }
    }

    public <Q, R> R execute(Endpoint<Q, R> endpoint, Q request) {
        return execute(endpoint, request, null);
    }

    String operatorId() { return credentials.operatorId(); }

    QueryTokenResponse acquireToken(Endpoint<QueryTokenRequest, QueryTokenResponse> tokenEndpoint) {
        return execute(tokenEndpoint, new QueryTokenRequest(credentials.operatorId(), credentials.operatorSecret()));
    }

    private synchronized RequestEnvelope createRequest(Object payload) {
        String timestamp = TIMESTAMP.format(clock.instant().atZone(options.protocolZone()));
        if (!timestamp.equals(lastTimestamp)) { lastTimestamp = timestamp; sequence = 0; }
        if (sequence == 9999) throw new IllegalStateException("Sequence capacity exhausted for this second");
        return codec.encodeRequest(payload, timestamp, String.format(Locale.ROOT, "%04d", ++sequence));
    }

    private static URI validateBaseUri(URI base, boolean allowHttpLoopback) {
        Objects.requireNonNull(base, "baseUri");
        if (!base.isAbsolute() || base.getHost() == null || base.getRawUserInfo() != null
                || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("Base URI must be an absolute server URI without credentials, query or fragment");
        }
        boolean loopback = "localhost".equalsIgnoreCase(base.getHost()) || "127.0.0.1".equals(base.getHost())
                || "[::1]".equals(base.getHost()) || "::1".equals(base.getHost());
        if (!"https".equalsIgnoreCase(base.getScheme())
                && !(allowHttpLoopback && loopback && "http".equalsIgnoreCase(base.getScheme()))) {
            throw new IllegalArgumentException("HTTPS is required (HTTP loopback must be explicitly enabled for tests)");
        }
        String raw = base.toASCIIString();
        return URI.create(raw.endsWith("/") ? raw : raw + "/");
    }

    @Override public void close() { http.close(); }
}
