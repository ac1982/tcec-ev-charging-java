package io.github.ac1982.tcec.spring;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.codec.TcecCodec;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.model.QueryTokenRequest;
import io.github.ac1982.tcec.model.QueryTokenResponse;
import io.github.ac1982.tcec.protocol.Endpoint;
import io.github.ac1982.tcec.security.PartnerCredentials;
import io.github.ac1982.tcec.security.TokenStore;
import io.github.ac1982.tcec.wire.RequestEnvelope;
import io.github.ac1982.tcec.wire.ResponseEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TcecAutoConfigurationTest {
    static final PartnerCredentials CREDENTIALS = new PartnerCredentials("123456789", "operator-secret",
            "1234567890abcdef", "1234567890abcdef", "1234567890abcdef");
    static final Endpoint<Payload, Payload> ENDPOINT = new Endpoint<>("notification_stationStatus", Payload.class, Payload.class, true, "/notification_stationStatus");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneId.of("UTC"));
    static final TcecCodec CODEC = new TcecCodec(CREDENTIALS);
    final WebApplicationContextRunner base = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TcecAutoConfiguration.class));
    final WebApplicationContextRunner enabled = base.withUserConfiguration(TestBeans.class)
            .withPropertyValues("tcec.server.enabled=true");

    public record Payload(@JsonProperty("Message") String message) {
        public Payload {
            if (message == null || message.isBlank()) throw new ProtocolException(4004, "Invalid payload");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestBeans {
        @Bean PartnerRegistry partners() { return new MapPartnerRegistry(List.of(CREDENTIALS)); }
        @Bean(name = "tcecClock") Clock clock() { return CLOCK; }
        @Bean AtomicInteger calls() { return new AtomicInteger(); }
        @Bean TcecEndpointHandler<Payload, Payload> statusHandler(AtomicInteger calls) {
            return TcecEndpointHandler.of(ENDPOINT, (request, context) -> {
                calls.incrementAndGet();
                if ("fail".equals(request.message())) throw new IllegalStateException("SECRET key and private details");
                return new Payload(context.operatorId() + ":" + request.message());
            });
        }
    }

    static MockMvc mvc(WebApplicationContext context) { return MockMvcBuilders.webAppContextSetup(context).build(); }
    static RequestEnvelope request(String seq) { return CODEC.encodeRequest(new Payload("中文"), "20261009160000", seq); }
    static String token(TokenStore tokens) { return tokens.issue(CREDENTIALS.operatorId(), Duration.ofHours(1)).value(); }
    static MvcResult send(MockMvc mvc, String endpoint, RequestEnvelope envelope, String token) throws Exception {
        var request = post(endpoint.equals("query_token") ? "/evcs/sdk/query_token" : "/" + endpoint).contentType(MediaType.APPLICATION_JSON).content(WireJson.write(envelope));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn();
    }
    static ResponseEnvelope envelope(MvcResult result) throws Exception {
        assertEquals(200, result.getResponse().getStatus());
        return WireJson.read(result.getResponse().getContentAsString(), ResponseEnvelope.class);
    }
    static int ret(MvcResult result) throws Exception {
        ResponseEnvelope response = envelope(result);
        if (response.ret() != 0) {
            assertEquals(response.ret(), assertThrows(ProtocolException.class,
                    () -> CODEC.decodeResponse(response, Payload.class)).ret());
        }
        return response.ret();
    }

    @Test void adapterIsOffUnlessExplicitlyEnabled() {
        base.withUserConfiguration(TestBeans.class).run(context -> assertThat(context).doesNotHaveBean(TcecController.class));
    }

    @Test void enabledAdapterRequiresTrustedPartnerConfiguration() {
        base.withPropertyValues("tcec.server.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test void completeConfigurationStartsWithOverrideClock() {
        enabled.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TcecController.class).hasSingleBean(TokenStore.class);
            assertSame(CLOCK, context.getBean("tcecClock"));
        });
    }

    @Test void tokenExchangeAndProtectedBusinessDispatch() {
        enabled.run(context -> {
            MockMvc mvc = mvc(context);
            var tokenRequest = CODEC.encodeRequest(new QueryTokenRequest(CREDENTIALS.operatorId(), CREDENTIALS.operatorSecret()), "20261009160000", "0001");
            QueryTokenResponse issued = CODEC.decodeResponse(envelope(send(mvc, "query_token", tokenRequest, null)), QueryTokenResponse.class);
            assertEquals(0, issued.succStat());
            assertEquals(7200, issued.tokenAvailableTime());
            assertFalse(issued.accessToken().isBlank());
            Payload result = CODEC.decodeResponse(envelope(send(mvc, ENDPOINT.path(), request("0002"), issued.accessToken())), Payload.class);
            assertEquals("123456789:中文", result.message());
            assertEquals(1, context.getBean(AtomicInteger.class).get());
        });
    }

    @Test void wrongOperatorSecretNeverIssuesToken() {
        enabled.run(context -> {
            var tokenRequest = CODEC.encodeRequest(new QueryTokenRequest(CREDENTIALS.operatorId(), "wrong-secret"), "20261009160000", "0001");
            QueryTokenResponse failed = CODEC.decodeResponse(envelope(send(mvc(context), "query_token", tokenRequest, null)), QueryTokenResponse.class);
            assertEquals(1, failed.succStat());
            assertEquals(2, failed.failReason());
            assertEquals("", failed.accessToken());
        });
    }

    @Test void queryTokenInnerIdentityMustMatchAuthenticatedPartner() {
        enabled.run(context -> {
            var tokenRequest = CODEC.encodeRequest(new QueryTokenRequest("987654321", CREDENTIALS.operatorSecret()), "20261009160000", "0001");
            QueryTokenResponse failed = CODEC.decodeResponse(envelope(send(mvc(context), "query_token", tokenRequest, null)), QueryTokenResponse.class);
            assertEquals(1, failed.succStat());
            assertEquals(1, failed.failReason());
        });
    }

    @Test void rejectsMissingTokenWithoutDispatchOrPoisoningReplayState() {
        enabled.run(context -> {
            MockMvc mvc = mvc(context);
            assertEquals(4002, ret(send(mvc, ENDPOINT.path(), request("0001"), null)));
            assertEquals(0, context.getBean(AtomicInteger.class).get());
            assertEquals(0, ret(send(mvc, ENDPOINT.path(), request("0001"), token(context.getBean(TokenStore.class)))));
        });
    }

    @Test void rejectsTamperingBeforeReplayClaimAndRejectsReplayAfterSuccess() {
        enabled.run(context -> {
            MockMvc mvc = mvc(context);
            String token = token(context.getBean(TokenStore.class));
            RequestEnvelope valid = request("0001");
            RequestEnvelope altered = new RequestEnvelope(valid.operatorId(), valid.data() + "x", valid.timeStamp(), valid.seq(), valid.sig());
            assertEquals(4001, ret(send(mvc, ENDPOINT.path(), altered, token)));
            assertEquals(0, ret(send(mvc, ENDPOINT.path(), valid, token)));
            assertEquals(4003, ret(send(mvc, ENDPOINT.path(), valid, token)));
            assertEquals(1, context.getBean(AtomicInteger.class).get());
        });
    }

    @Test void rejectsExpiredRequestAndInvalidPayloadWithoutDispatch() {
        enabled.run(context -> {
            MockMvc mvc = mvc(context);
            String token = token(context.getBean(TokenStore.class));
            RequestEnvelope old = CODEC.encodeRequest(new Payload("old"), "20261008160000", "0001");
            assertEquals(4003, ret(send(mvc, ENDPOINT.path(), old, token)));
            RequestEnvelope invalid = CODEC.encodeRequest(java.util.Map.of("Message", ""), "20261009160000", "0002");
            assertEquals(4004, ret(send(mvc, ENDPOINT.path(), invalid, token)));
            assertEquals(0, context.getBean(AtomicInteger.class).get());
        });
    }

    @Test void unknownPartnerAndMalformedEnvelopeHaveNoUnsignedProtocolBody() {
        enabled.run(context -> {
            MockMvc mvc = mvc(context);
            RequestEnvelope valid = request("0001");
            RequestEnvelope unknown = new RequestEnvelope("987654321", valid.data(), valid.timeStamp(), valid.seq(), valid.sig());
            MvcResult result = send(mvc, ENDPOINT.path(), unknown, "invalid-token");
            assertEquals(401, result.getResponse().getStatus());
            assertEquals("", result.getResponse().getContentAsString());
            assertEquals(400, mvc.perform(post("/evcs/sdk/query_token").contentType(MediaType.APPLICATION_JSON).content("not json")).andReturn().getResponse().getStatus());
        });
    }

    @Test void oversizeAndInvalidUtf8AreRejectedBeforeLookup() {
        enabled.withPropertyValues("tcec.server.max-request-bytes=500").run(context -> {
            MockMvc mvc = mvc(context);
            assertEquals(413, mvc.perform(post("/evcs/sdk/query_token").contentType(MediaType.APPLICATION_JSON).content(new byte[501])).andReturn().getResponse().getStatus());
            assertEquals(400, mvc.perform(post("/evcs/sdk/query_token").contentType(MediaType.APPLICATION_JSON).content(new byte[]{(byte)0xc3, (byte)0x28})).andReturn().getResponse().getStatus());
        });
    }

    @Test void handlerFailureIsSignedAndDoesNotDiscloseDetails() {
        enabled.run(context -> {
            MockMvc mvc = mvc(context);
            RequestEnvelope failed = CODEC.encodeRequest(new Payload("fail"), "20261009160000", "0001");
            MvcResult result = send(mvc, ENDPOINT.path(), failed, token(context.getBean(TokenStore.class)));
            assertEquals(500, ret(result));
            assertFalse(result.getResponse().getContentAsString().contains("SECRET"));
        });
    }

    @Test void strictBearerParsingRejectsDuplicateAndMalformedHeaders() {
        enabled.run(context -> {
            MockMvc mvc = mvc(context);
            String token = token(context.getBean(TokenStore.class));
            for (String value : List.of("bearer" + token, "Bearer  " + token, "Basic " + token)) {
                MvcResult result = mvc.perform(post("/" + ENDPOINT.path()).contentType(MediaType.APPLICATION_JSON)
                        .content(WireJson.write(request("0001"))).header("Authorization", value)).andReturn();
                assertEquals(4002, ret(result));
            }
            MvcResult duplicate = mvc.perform(post("/" + ENDPOINT.path()).contentType(MediaType.APPLICATION_JSON)
                    .content(WireJson.write(request("0001"))).header("Authorization", "Bearer " + token, "Bearer " + token)).andReturn();
            assertEquals(4002, ret(duplicate));
            assertEquals(0, context.getBean(AtomicInteger.class).get());
        });
    }

    @Test void invalidPolicyFailsAtStartup() {
        enabled.withPropertyValues("tcec.server.token-ttl=8d").run(context -> assertThat(context).hasFailed());
    }

    @Test void duplicatePartnersAndReservedHandlersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MapPartnerRegistry(List.of(CREDENTIALS, CREDENTIALS)));
        enabled.withBean("duplicateHandler", TcecEndpointHandler.class,
                () -> TcecEndpointHandler.of(ENDPOINT, (value, context) -> value))
                .run(context -> assertThat(context).hasFailed());
    }
}
