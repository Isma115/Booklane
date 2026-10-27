package com.booklane.api.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.booklane.api.identity.DemoUserCatalog;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class LocalConfigurationTest {
    private Map<String, String> valid() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("users-file", Path.of(System.getProperty("booklane.root"), "infra/demo/users.json").toString());
        values.put("internal-username", "booklane-internal");
        values.put("internal-password", "a".repeat(64));
        values.put("kafka-username", "booklane-api");
        values.put("kafka-password", "b".repeat(64));
        values.put("kafka-bootstrap-servers", "127.0.0.1:19092");
        values.put("booking-base-url", "http://127.0.0.1:8082");
        return values;
    }

    private ApplicationContextRunner runner(Map<String, String> values) {
        return new ApplicationContextRunner().withUserConfiguration(LocalConfiguration.class)
                .withPropertyValues(values.entrySet().stream()
                        .map(e -> "booklane." + e.getKey() + "=" + e.getValue()).toArray(String[]::new));
    }

    @Test void createsTypedConfigurationAndCatalogAtStartup() {
        runner(valid()).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ApiProperties.class).hasSingleBean(DemoUserCatalog.class);
            assertThat(context.getBean(ApiProperties.class).toString()).contains("redacted").doesNotContain("a".repeat(64));
            assertThat(context.getBean(DemoUserCatalog.class).size()).isEqualTo(3);
        });
    }

    @Test void everyMissingRequiredValueFailsWithoutPrintingSecrets(CapturedOutput output) {
        for (String key : valid().keySet()) {
            var values = valid();
            values.remove(key);
            runner(values).run(context -> assertThat(context).hasFailed()
                    .getFailure().hasStackTraceContaining("booklane." + key));
        }
        assertThat(output.getAll()).doesNotContain("a".repeat(64), "b".repeat(64), "c".repeat(64));
    }

    @Test void malformedSecretAndCredentialBearingUrlAreSanitized(CapturedOutput output) {
        var values = valid();
        values.put("internal-password", "SENSITIVE_CONFIG_MARKER");
        runner(values).run(context -> assertThat(context).hasFailed()
                .getFailure().hasStackTraceContaining("booklane.internal-password"));
        values = valid();
        values.put("booking-base-url", "http://user:SENSITIVE_CONFIG_MARKER@127.0.0.1:8082");
        runner(values).run(context -> assertThat(context).hasFailed()
                .getFailure().hasStackTraceContaining("booklane.booking-base-url")
                .hasStackTraceContaining("LocalConfiguration"));
        assertThat(output.getAll()).doesNotContain("SENSITIVE_CONFIG_MARKER");
    }

    @Test void localAndDevProfilesSupplyTheirOwnNetworkDefaults() {
        for (String profile : new String[] {"local", "dev"}) {
            var values = valid();
            values.remove("kafka-bootstrap-servers");
            values.remove("booking-base-url");
            runner(values).withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues("spring.profiles.active=" + profile).run(context -> {
                assertThat(context).hasNotFailed();
                var properties = context.getBean(ApiProperties.class);
                assertThat(properties.kafkaBootstrapServers()).isEqualTo(profile.equals("local") ? "kafka:9092" : "127.0.0.1:19092");
                assertThat(properties.bookingBaseUrl()).isEqualTo(profile.equals("local")
                        ? "http://booklane-booking:8082" : "http://127.0.0.1:8082");
            });
        }
    }
}
