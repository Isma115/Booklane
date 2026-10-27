package com.booklane.api.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("booklane")
public record ApiProperties(String usersFile, String bookingBaseUrl,
        String internalUsername, String internalPassword, String kafkaBootstrapServers,
        String kafkaUsername, String kafkaPassword) {

    public void validate() {
        required(usersFile, "users-file");
        required(internalUsername, "internal-username");
        require("booklane-internal".equals(internalUsername), "internal-username");
        secret(internalPassword, "internal-password");
        require("booklane-api".equals(kafkaUsername), "kafka-username");
        secret(kafkaPassword, "kafka-password");
        required(kafkaBootstrapServers, "kafka-bootstrap-servers");
        require(kafkaBootstrapServers.length() <= 1000, "kafka-bootstrap-servers");
        for (String server : kafkaBootstrapServers.split(",", -1)) {
            URI uri = uri("tcp://" + server, "kafka-bootstrap-servers");
            require(uri.getHost() != null && uri.getPort() > 0 && uri.getPort() <= 65535
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getPath().isEmpty(), "kafka-bootstrap-servers");
        }
        required(bookingBaseUrl, "booking-base-url");
        URI booking = uri(bookingBaseUrl, "booking-base-url");
        require(("http".equals(booking.getScheme()) || "https".equals(booking.getScheme()))
                && booking.getHost() != null && booking.getUserInfo() == null && booking.getQuery() == null
                && booking.getFragment() == null && (booking.getPath().isEmpty() || "/".equals(booking.getPath()))
                && (booking.getPort() == -1 || booking.getPort() > 0 && booking.getPort() <= 65535), "booking-base-url");
    }

    private static URI uri(String value, String key) {
        try { return URI.create(value); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid configuration: booklane." + key); }
    }

    private static void required(String value, String key) {
        require(value != null && !value.isBlank(), key);
    }

    private static void secret(String value, String key) {
        require(value != null && value.matches("[0-9a-f]{64}"), key);
    }

    private static void require(boolean valid, String key) {
        if (!valid) throw new IllegalStateException("Missing or invalid configuration: booklane." + key);
    }

    @Override public String toString() { return "ApiProperties[redacted]"; }
}
