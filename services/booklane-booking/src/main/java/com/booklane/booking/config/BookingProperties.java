package com.booklane.booking.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("booklane")
public record BookingProperties(String usersFile, String databaseUrl, String databaseUsername, String databasePassword,
        String internalUsername, String internalPassword, String kafkaBootstrapServers,
        String kafkaUsername, String kafkaPassword) {

    public void validate() {
        required(usersFile, "users-file");
        required(internalUsername, "internal-username");
        require("booklane-internal".equals(internalUsername), "internal-username");
        secret(internalPassword, "internal-password");
        require("booklane-booking".equals(kafkaUsername), "kafka-username");
        secret(kafkaPassword, "kafka-password");
        required(kafkaBootstrapServers, "kafka-bootstrap-servers");
        require(kafkaBootstrapServers.length() <= 1000, "kafka-bootstrap-servers");
        for (String server : kafkaBootstrapServers.split(",", -1)) {
            URI uri = uri("tcp://" + server, "kafka-bootstrap-servers");
            require(uri.getHost() != null && uri.getPort() > 0 && uri.getPort() <= 65535
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getPath().isEmpty(), "kafka-bootstrap-servers");
        }
        required(databaseUrl, "database-url");
        require(databaseUrl.startsWith("jdbc:postgresql://"), "database-url");
        URI database = uri(databaseUrl.substring(5), "database-url");
        require(database.getHost() != null && database.getPort() > 0 && database.getPort() <= 65535
                && database.getUserInfo() == null && database.getQuery() == null && database.getFragment() == null
                && database.getPath().matches("/[A-Za-z0-9_]+"), "database-url");
        require("booklane_booking".equals(databaseUsername), "database-username");
        secret(databasePassword, "database-password");
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

    @Override public String toString() { return "BookingProperties[redacted]"; }
}
