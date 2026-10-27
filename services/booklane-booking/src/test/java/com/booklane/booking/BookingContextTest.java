package com.booklane.booking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("isolated")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingContextTest {
    @Autowired
    ApplicationContext context;

    @Test
    void startsServletServerWithPersistenceExplicitlyDisabledForThisTest() throws Exception {
        assertThat(context).isInstanceOf(ServletWebServerApplicationContext.class);
        var server = (ServletWebServerApplicationContext) context;
        assertThat(server.getWebServer().getClass().getName()).contains("Tomcat");
        assertThat(context.containsBean("dataSource")).isFalse();
        assertThat(context.containsBean("entityManagerFactory")).isFalse();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.getWebServer().getPort() + "/internal/bookings"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        }
    }
}
