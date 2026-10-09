package io.github.ac1982.tcec.spring;

import io.github.ac1982.tcec.security.InMemoryReplayStore;
import io.github.ac1982.tcec.security.InMemoryTokenStore;
import io.github.ac1982.tcec.security.ReplayStore;
import io.github.ac1982.tcec.security.RequestVerifier;
import io.github.ac1982.tcec.security.TokenStore;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/** Explicitly opt in with tcec.server.enabled=true and supply a PartnerRegistry bean. */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "tcec.server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TcecServerProperties.class)
public class TcecAutoConfiguration {
    @Bean("tcecClock")
    @ConditionalOnMissingBean(name = "tcecClock")
    Clock tcecClock() { return Clock.systemUTC(); }

    @Bean
    @ConditionalOnMissingBean(ReplayStore.class)
    ReplayStore tcecReplayStore(@Qualifier("tcecClock") Clock clock, TcecServerProperties properties) {
        properties.validate();
        if (properties.isRequireSharedStores()) throw new IllegalStateException("Configure a shared ReplayStore bean");
        return new InMemoryReplayStore(clock, properties.getReplayCapacity());
    }

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    TokenStore tcecTokenStore(@Qualifier("tcecClock") Clock clock, TcecServerProperties properties) {
        properties.validate();
        if (properties.isRequireSharedStores()) throw new IllegalStateException("Configure a shared TokenStore bean");
        return new InMemoryTokenStore(clock, properties.getTokenCapacity());
    }

    @Bean
    @ConditionalOnMissingBean(RequestVerifier.class)
    RequestVerifier tcecRequestVerifier(@Qualifier("tcecClock") Clock clock, ReplayStore replayStore,
                                       TcecServerProperties properties) {
        properties.validate();
        return new RequestVerifier(clock, properties.getProtocolZone(), properties.getAllowedClockSkew(), replayStore);
    }

    @Bean
    @ConditionalOnMissingBean(TcecController.class)
    TcecController tcecController(PartnerRegistry registry, RequestVerifier verifier, TokenStore tokens, ReplayStore replay,
                                  List<TcecEndpointHandler<?, ?>> handlers, TcecServerProperties properties,
                                  @Qualifier("tcecClock") Clock clock) {
        if (properties.isRequireSharedStores()
                && (tokens instanceof InMemoryTokenStore || replay instanceof InMemoryReplayStore))
            throw new IllegalStateException("Shared stores are required; single-JVM store beans are not allowed");
        return new TcecController(registry, verifier, tokens, handlers, properties, clock);
    }
}
