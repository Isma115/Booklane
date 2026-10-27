package com.booklane.api.config;

import com.booklane.api.identity.DemoUserCatalog;
import java.nio.file.Path;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiProperties.class)
public class LocalConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateLocalConfiguration(ConfigurableEnvironment environment) {
        return factory -> {
            // Validate before JPA/DataSource or any other dependency can start.
            var properties = Binder.get(environment).bind("booklane", ApiProperties.class)
                    .orElseThrow(() -> new IllegalStateException("Missing configuration: booklane"));
            properties.validate();
            factory.registerSingleton("demoUserCatalog", DemoUserCatalog.load(Path.of(properties.usersFile())));
        };
    }
}
