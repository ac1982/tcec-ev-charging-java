package io.github.ac1982.tcec.spring;

import io.github.ac1982.tcec.client.ClientOptions;
import io.github.ac1982.tcec.client.TcecClient;
import io.github.ac1982.tcec.model.QueryTokenRequest;
import io.github.ac1982.tcec.model.QueryTokenResponse;
import io.github.ac1982.tcec.protocol.Endpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the actual Boot servlet server, auto-configuration discovery, and JDK HTTP client together. */
class TcecHttpIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(TcecAutoConfigurationTest.TestBeans.class)
    static class TestApplication {}

    @Test void fullBootServerAuthenticatesAndDispatchesOverRealHttp() {
        try (var context = new SpringApplicationBuilder(TestApplication.class)
                .properties("server.port=0", "server.address=127.0.0.1", "tcec.server.enabled=true",
                        "spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            assertEquals("4.1.1", SpringBootVersion.getVersion());
            assertNotNull(context.getBean(TcecController.class));
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            ClientOptions options = new ClientOptions(Duration.ofSeconds(3), Duration.ofSeconds(10),
                    65536, ZoneId.of("Asia/Shanghai"), true);
            try (var client = new TcecClient(URI.create("http://127.0.0.1:" + port + "/evcs/v1"),
                    TcecAutoConfigurationTest.CREDENTIALS, options, TcecAutoConfigurationTest.CLOCK)) {
                QueryTokenResponse token = client.execute(Endpoints.QUERY_TOKEN,
                        new QueryTokenRequest("123456789", "operator-secret"));
                assertEquals(0, token.succStat());
                var reply = client.execute(TcecAutoConfigurationTest.ENDPOINT,
                        new TcecAutoConfigurationTest.Payload("actual HTTP"), token.accessToken());
                assertEquals("123456789:actual HTTP", reply.message());
            }
        }
    }
}
