package com.booklane.booking.config;

import com.booklane.booking.security.DemoIdentityCatalog;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BookingProperties.class)
public class LocalConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    static BeanFactoryPostProcessor validateLocalConfiguration(ConfigurableEnvironment environment) {
        return factory -> {
            // Validate before JPA/DataSource or any other dependency can start.
            var properties = Binder.get(environment).bind("booklane", BookingProperties.class)
                    .orElseThrow(() -> new IllegalStateException("Missing configuration: booklane"));
            properties.validate();
            factory.registerSingleton("demoIdentityCatalog", DemoIdentityCatalog.load(Path.of(properties.usersFile())));
        };
    }
}
