package com.booklane.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("isolated")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContextTest {
    @Autowired
    ApplicationContext context;

    @Test
    void startsReactiveServerWithoutPersistenceOrBusinessEndpoints() throws Exception {
        assertThat(context).isInstanceOf(ReactiveWebServerApplicationContext.class);
        var server = (ReactiveWebServerApplicationContext) context;
        assertThat(server.getWebServer().getClass().getName()).contains("Netty");
        assertThat(context.containsBean("dataSource")).isFalse();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.getWebServer().getPort() + "/api/bookings"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        }
    }
}
