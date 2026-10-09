package io.github.ac1982.tcec.spring;

import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.codec.TcecCodec;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.model.QueryTokenRequest;
import io.github.ac1982.tcec.model.QueryTokenResponse;
import io.github.ac1982.tcec.protocol.Endpoint;
import io.github.ac1982.tcec.protocol.Endpoints;
import io.github.ac1982.tcec.security.AccessToken;
import io.github.ac1982.tcec.security.PartnerCredentials;
import io.github.ac1982.tcec.security.RequestVerifier;
import io.github.ac1982.tcec.security.TokenStore;
import io.github.ac1982.tcec.wire.RequestEnvelope;
import io.github.ac1982.tcec.wire.ResponseEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bounded receiving adapter. No unauthenticated business request reaches a handler. */
@RestController
@RequestMapping("${tcec.server.base-path:}")
public final class TcecController {
    private final PartnerRegistry partners;
    private final RequestVerifier verifier;
    private final TokenStore tokens;
    private final Map<String, TcecEndpointHandler<?, ?>> handlers;
    private final TcecServerProperties properties;
    private final Clock clock;

    public TcecController(PartnerRegistry partners, RequestVerifier verifier, TokenStore tokens,
                          List<TcecEndpointHandler<?, ?>> handlers, TcecServerProperties properties) {
        this(partners, verifier, tokens, handlers, properties, Clock.systemUTC());
    }

    public TcecController(PartnerRegistry partners, RequestVerifier verifier, TokenStore tokens,
                          List<TcecEndpointHandler<?, ?>> handlers, TcecServerProperties properties, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.partners = Objects.requireNonNull(partners, "partners");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.properties = Objects.requireNonNull(properties, "properties");
        properties.validate();
        Map<String, TcecEndpointHandler<?, ?>> mapped = new HashMap<>();
        for (TcecEndpointHandler<?, ?> handler : handlers) {
            Endpoint<?, ?> endpoint = handler.endpoint();
            if ("query_token".equals(endpoint.path()) || !endpoint.tokenRequired())
                throw new IllegalArgumentException("Business handlers must require a token and cannot replace query_token");
            String path = route(endpoint);
            if (!(path.equals("/" + endpoint.path()) || path.equals("/evcs/v1/" + endpoint.path())
                    || path.equals("/evcs/sdk/" + endpoint.path())))
                throw new IllegalArgumentException("Handler must use a root, /evcs/v1, or /evcs/sdk route");
            if (mapped.putIfAbsent(path, handler) != null) throw new IllegalArgumentException("Duplicate endpoint handler");
        }
        this.handlers = Map.copyOf(mapped);
    }

    @PostMapping(path = {"/{endpoint}", "/evcs/v1/{endpoint}", "/evcs/sdk/{endpoint}"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receive(@PathVariable("endpoint") String endpoint, HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String prefix = "/".equals(properties.getBasePath()) ? "" : properties.getBasePath();
        if (!prefix.isEmpty() && path.startsWith(prefix + "/")) path = path.substring(prefix.length());
        boolean tokenRoute = path.equals(Endpoints.QUERY_TOKEN.outboundPath())
                || path.equals("/evcs/v1/query_token") || path.equals("/query_token");
        // Never silently route the station-status notification to the SDK's observed wrong start-charge path.
        // Route families are exact: root callbacks, /evcs/v1 business, /evcs/sdk locks and token.
        if (!tokenRoute && !handlers.containsKey(path)) return ResponseEntity.notFound().build();
        RequestEnvelope envelope;
        try {
            if (request.getContentLengthLong() > properties.getMaxRequestBytes()) return ResponseEntity.status(413).build();
            byte[] body = request.getInputStream().readNBytes(properties.getMaxRequestBytes() + 1);
            if (body.length > properties.getMaxRequestBytes()) return ResponseEntity.status(413).build();
            envelope = WireJson.read(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(body)).toString(), RequestEnvelope.class);
            if (envelope == null || envelope.operatorId() == null) return ResponseEntity.badRequest().build();
        } catch (IOException | RuntimeException malformed) {
            return ResponseEntity.badRequest().build();
        }
        PartnerCredentials credentials;
        try {
            credentials = partners.find(envelope.operatorId()).orElse(null);
        } catch (RuntimeException unavailable) {
            return ResponseEntity.status(503).build();
        }
        // Unknown peers have no trusted signing key. Do not return an unsigned protocol-success envelope.
        if (credentials == null) return ResponseEntity.status(401).build();
        TcecCodec codec = new TcecCodec(credentials);
        try {
            codec.verifyRequest(envelope);
            if (tokenRoute) {
                verifier.verify(envelope);
                return signed(codec.encodeResponse(queryToken(codec, credentials, envelope)));
            }
            String token = bearer(request);
            if (token == null || !tokens.validate(credentials.operatorId(), token)) {
                return signed(codec.encodeError(4002, "Invalid access token"));
            }
            verifier.verify(envelope);
            TcecEndpointHandler<?, ?> handler = handlers.get(path);
            if (handler == null) return signed(codec.encodeError(4004, "Endpoint is not configured"));
            return signed(dispatch(handler, envelope, codec));
        } catch (ProtocolException failure) {
            return signed(codec.encodeError(failure.ret(), safeMessage(failure.ret())));
        } catch (RuntimeException failure) {
            return signed(codec.encodeError(500, "System error"));
        }
    }

    private QueryTokenResponse queryToken(TcecCodec codec, PartnerCredentials credentials, RequestEnvelope envelope) {
        QueryTokenRequest request = codec.decodeRequest(envelope, QueryTokenRequest.class);
        if (!credentials.operatorId().equals(request.operatorId())) {
            return new QueryTokenResponse(credentials.operatorId(), 1, "", 0, 1);
        }
        if (!credentials.verifiesOperatorSecret(request.operatorSecret())) {
            return new QueryTokenResponse(credentials.operatorId(), 1, "", 0, 2);
        }
        // Report lifetime at issuance start; clients count from their earlier HTTP request start.
        // This also preserves one-second TTLs without extending a reused token's stored expiry.
        var issuanceStarted = clock.instant();
        AccessToken token = tokens.issue(credentials.operatorId(), properties.getTokenTtl());
        long remainingSeconds = Duration.between(issuanceStarted, token.expiresAt()).getSeconds();
        if (!token.expiresAt().isAfter(clock.instant())
                || remainingSeconds < 1 || remainingSeconds > Duration.ofDays(7).getSeconds()
                || token.value().length() > 4096 || !token.value().matches("[A-Za-z0-9\\-._~+/]+=*"))
            throw new ProtocolException(-1, "Token store returned an unusable token");
        return new QueryTokenResponse(credentials.operatorId(), 0, token.value(),
                Math.toIntExact(remainingSeconds), 0);
    }

    private static <Q, R> ResponseEnvelope dispatch(TcecEndpointHandler<Q, R> handler,
                                                   RequestEnvelope envelope, TcecCodec codec) {
        Q payload = codec.decodeRequest(envelope, handler.endpoint().requestType());
        R response = handler.handle(payload, new RequestContext(envelope.operatorId(), envelope.timeStamp(), envelope.seq()));
        if (response == null || !handler.endpoint().responseType().isInstance(response))
            throw new IllegalStateException("Handler returned an invalid response");
        return codec.encodeResponse(response);
    }

    private static String route(Endpoint<?, ?> endpoint) {
        return endpoint.outboundPath().startsWith("/") ? endpoint.outboundPath() : "/" + endpoint.outboundPath();
    }

    private static String bearer(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) return null;
        String value = headers.nextElement();
        if (headers.hasMoreElements() || value == null || value.length() > 4103
                || !value.matches("(?i:Bearer) [A-Za-z0-9\\-._~+/]+=*")) return null;
        return value.substring(7);
    }

    private static ResponseEntity<String> signed(ResponseEnvelope response) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(WireJson.write(response));
    }

    private static String safeMessage(int ret) {
        return switch (ret) {
            case 4001 -> "Invalid signature";
            case 4002 -> "Invalid access token";
            case 4003 -> "Invalid envelope";
            case 4004 -> "Invalid business payload";
            case -1 -> "Service busy";
            default -> "System error";
        };
    }
}
