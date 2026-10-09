package io.github.ac1982.tcec.spring;

import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.model.QueryTokenRequest;
import io.github.ac1982.tcec.model.QueryTokenResponse;
import io.github.ac1982.tcec.protocol.Endpoint;
import io.github.ac1982.tcec.protocol.Endpoints;
import io.github.ac1982.tcec.security.AccessToken;
import io.github.ac1982.tcec.security.InMemoryReplayStore;
import io.github.ac1982.tcec.security.ReplayStore;
import io.github.ac1982.tcec.security.TokenStore;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static io.github.ac1982.tcec.spring.TcecAutoConfigurationTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TcecRouteAndStoreTest {
    final WebApplicationContextRunner enabled = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TcecAutoConfiguration.class))
            .withUserConfiguration(RequiredBeans.class).withPropertyValues("tcec.server.enabled=true");

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class RequiredBeans {
        @Bean PartnerRegistry partners() { return new MapPartnerRegistry(List.of(CREDENTIALS)); }
        @Bean(name = "tcecClock") Clock clock() { return CLOCK; }
    }

    static MvcResult postAt(MockMvc mvc, String path, Object request, String seq, String token) throws Exception {
        var post = post(path).contentType(MediaType.APPLICATION_JSON)
                .content(WireJson.write(CODEC.encodeRequest(request, "20261009160000", seq)));
        if (token != null) post.header("Authorization", "Bearer " + token);
        return mvc.perform(post).andReturn();
    }

    static QueryTokenRequest tokenRequest() {
        return new QueryTokenRequest(CREDENTIALS.operatorId(), CREDENTIALS.operatorSecret());
    }

    @Test void all29CanonicalRoutesAndRootTokenAliasAreReachable() {
        AtomicInteger calls = new AtomicInteger();
        WebApplicationContextRunner configured = enabled;
        for (Endpoint<?, ?> endpoint : Endpoints.all()) {
            if (endpoint == Endpoints.QUERY_TOKEN) continue;
            Endpoint<Payload, Payload> synthetic = new Endpoint<>(endpoint.path(), Payload.class, Payload.class,
                    true, endpoint.outboundPath());
            configured = configured.withBean("handler_" + endpoint.path(), TcecEndpointHandler.class,
                    () -> TcecEndpointHandler.of(synthetic, (value, context) -> {
                        calls.incrementAndGet();
                        return new Payload(endpoint.outboundPath());
                    }));
        }
        configured.run(context -> {
            MockMvc mvc = mvc(context);
            QueryTokenResponse issued = CODEC.decodeResponse(envelope(postAt(mvc, "/evcs/sdk/query_token", tokenRequest(), "0001", null)), QueryTokenResponse.class);
            QueryTokenResponse alias = CODEC.decodeResponse(envelope(postAt(mvc, "/query_token", tokenRequest(), "0002", null)), QueryTokenResponse.class);
            assertEquals(issued.accessToken(), alias.accessToken());
            int seq = 3;
            for (Endpoint<?, ?> endpoint : Endpoints.all()) {
                if (endpoint == Endpoints.QUERY_TOKEN) continue;
                Payload response = CODEC.decodeResponse(envelope(postAt(mvc, endpoint.outboundPath(), new Payload("ok"),
                        String.format("%04d", seq++), issued.accessToken())), Payload.class);
                assertEquals(endpoint.outboundPath(), response.message());
            }
            assertEquals(28, calls.get());
            QueryTokenResponse cachedPath = CODEC.decodeResponse(envelope(postAt(mvc, "/evcs/v1/query_token", tokenRequest(), "0099", null)), QueryTokenResponse.class);
            assertEquals(issued.accessToken(), cachedPath.accessToken());
            assertEquals(4003, ret(postAt(mvc, "/query_token", tokenRequest(), "0099", null)));
        });
    }

    @Test void correctStationStatusRouteDoesNotAliasTheObservedSdkStartChargeBug() {
        enabled.withBean("statusHandler", TcecEndpointHandler.class,
                () -> TcecEndpointHandler.of(ENDPOINT, (request, context) -> request)).run(context -> {
            MockMvc mvc = mvc(context);
            String token = token(context.getBean(TokenStore.class));
            assertEquals(0, ret(postAt(mvc, "/notification_stationStatus", new Payload("ok"), "0001", token)));
            for (String wrong : List.of("/notification_start_charge_result", "/evcs/v1/notification_stationStatus", "/evcs/sdk/notification_stationStatus"))
                assertEquals(404, postAt(mvc, wrong, new Payload("ok"), "0002", token).getResponse().getStatus());
        });
    }

    @Test void rootAndNestedPrefixConfigurationAreSupportedExplicitly() {
        for (String prefix : List.of("", "/", "/partner")) {
            enabled.withPropertyValues("tcec.server.base-path=" + prefix).run(context -> {
                String actualPrefix = "/".equals(prefix) ? "" : prefix;
                assertEquals(0, ret(postAt(mvc(context), actualPrefix + "/evcs/sdk/query_token", tokenRequest(), "0001", null)));
                assertEquals(0, ret(postAt(mvc(context), actualPrefix + "/query_token", tokenRequest(), "0002", null)));
            });
        }
    }

    @Test void tokenResponseUsesStoreRemainingLifetimeInsteadOfConfiguredTtl() {
        TokenStore store = new TokenStore() {
            @Override public AccessToken issue(String id, Duration ttl) { return new AccessToken("short-token", CLOCK.instant().plusSeconds(30)); }
            @Override public boolean validate(String id, String token) { return token.equals("short-token"); }
        };
        enabled.withBean(TokenStore.class, () -> store).run(context -> {
            assertSame(store, context.getBean(TokenStore.class));
            QueryTokenResponse response = CODEC.decodeResponse(envelope(postAt(mvc(context), "/query_token", tokenRequest(), "0001", null)), QueryTokenResponse.class);
            assertEquals(30, response.tokenAvailableTime());
        });
    }

    @Test void expiredOrMalformedExternalTokenIsNotReturnedAsSuccess() {
        for (AccessToken invalid : List.of(new AccessToken("expired", CLOCK.instant()),
                new AccessToken("x\r\nHeader: injected", CLOCK.instant().plusSeconds(60)))) {
            TokenStore store = new TokenStore() {
                @Override public AccessToken issue(String id, Duration ttl) { return invalid; }
                @Override public boolean validate(String id, String token) { return false; }
            };
            enabled.withBean(TokenStore.class, () -> store).run(context ->
                    assertEquals(-1, ret(postAt(mvc(context), "/query_token", tokenRequest(), "0001", null))));
        }
    }

    @Test void productionCanRequireBothSharedStoreBeansWithoutSilentInMemoryFallback() {
        enabled.withPropertyValues("tcec.server.require-shared-stores=true").run(context -> assertThat(context).hasFailed());
        TokenStore tokens = new TokenStore() {
            @Override public AccessToken issue(String id, Duration ttl) { return new AccessToken("shared-token", CLOCK.instant().plus(ttl)); }
            @Override public boolean validate(String id, String token) { return "shared-token".equals(token); }
        };
        ReplayStore replay = (operator, timestamp, identity, expiry) -> true; // Synthetic external SPI fixture only.
        enabled.withPropertyValues("tcec.server.require-shared-stores=true")
                .withBean(TokenStore.class, () -> tokens)
                .withBean(ReplayStore.class, () -> new InMemoryReplayStore(CLOCK, 100))
                .run(context -> assertThat(context).hasFailed());
        enabled.withPropertyValues("tcec.server.require-shared-stores=true")
                .withBean(TokenStore.class, () -> tokens).withBean(ReplayStore.class, () -> replay).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertSame(tokens, context.getBean(TokenStore.class));
                    assertSame(replay, context.getBean(ReplayStore.class));
                });
    }

    @Test void nonTokenHandlersCannotDisableBearerAuthentication() {
        Endpoint<Payload, Payload> unsafe = new Endpoint<>("public_business", Payload.class, Payload.class, false);
        enabled.withBean("unsafeHandler", TcecEndpointHandler.class,
                () -> TcecEndpointHandler.of(unsafe, (value, context) -> value)).run(context -> assertThat(context).hasFailed());
    }

    @Test void invalidPrefixOrCacheCapacityFailsAtStartup() {
        for (String path : List.of("relative", "/trailing/", "/../escape", "//host", "/%2e%2e"))
            enabled.withPropertyValues("tcec.server.base-path=" + path).run(context -> assertThat(context).hasFailed());
        enabled.withPropertyValues("tcec.server.token-capacity=0").run(context -> assertThat(context).hasFailed());
    }

    @Test void storeFailureIsSignedAndNeverInvokesBusinessHandler() {
        AtomicInteger calls = new AtomicInteger();
        TokenStore unavailable = new TokenStore() {
            @Override public AccessToken issue(String id, Duration ttl) { throw new IllegalStateException("SECRET store details"); }
            @Override public boolean validate(String id, String token) { throw new IllegalStateException("SECRET store details"); }
        };
        enabled.withBean(TokenStore.class, () -> unavailable).withBean("handler", TcecEndpointHandler.class,
                () -> TcecEndpointHandler.of(ENDPOINT, (value, context) -> { calls.incrementAndGet(); return value; })).run(context -> {
            var response = postAt(mvc(context), ENDPOINT.outboundPath(), new Payload("ok"), "0001", "token");
            assertEquals(500, ret(response));
            assertFalse(response.getResponse().getContentAsString().contains("SECRET"));
            assertEquals(0, calls.get());
        });
    }

    @Test void authenticatedButIncompleteTypedRequestCannotReachTheHandler() {
        AtomicInteger calls = new AtomicInteger();
        enabled.withBean("locksHandler", TcecEndpointHandler.class, () -> TcecEndpointHandler.of(Endpoints.QUERY_GROUND_LOCKS,
                (value, context) -> { calls.incrementAndGet(); return new io.github.ac1982.tcec.model.QueryGroundLocksResponse(); }))
                .run(context -> {
                    assertEquals(4004, ret(postAt(mvc(context), Endpoints.QUERY_GROUND_LOCKS.outboundPath(),
                            new io.github.ac1982.tcec.model.QueryGroundLocksRequest(null), "0001", token(context.getBean(TokenStore.class)))));
                    assertEquals(0, calls.get());
                });
    }

}
