package com.booklane.booking.config;

import com.booklane.booking.security.DemoIdentityCatalog;
import com.booklane.booking.security.InternalPrincipalFilter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalSecurityConfig {
    @Bean
    AuthenticationProvider internalAuthenticationProvider(BookingProperties properties) {
        return new AuthenticationProvider() {
            @Override public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                byte[] expectedUser = properties.internalUsername().getBytes(StandardCharsets.UTF_8);
                byte[] actualUser = authentication.getName().getBytes(StandardCharsets.UTF_8);
                byte[] expectedPassword = properties.internalPassword().getBytes(StandardCharsets.UTF_8);
                byte[] actualPassword = String.valueOf(authentication.getCredentials()).getBytes(StandardCharsets.UTF_8);
                boolean valid = MessageDigest.isEqual(expectedUser, actualUser) & MessageDigest.isEqual(expectedPassword, actualPassword);
                if (!valid) throw new BadCredentialsException("Invalid service credentials");
                return UsernamePasswordAuthenticationToken.authenticated("internal-service", null,
                        List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
            }
            @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
        };
    }

    @Bean
    SecurityFilterChain internalSecurity(HttpSecurity http, AuthenticationProvider provider, DemoIdentityCatalog identities) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, error) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"booklane-internal\"");
            InternalPrincipalFilter.error(response, 401, "UNAUTHENTICATED", "Service authentication required");
        };
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(provider)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, error) ->
                                InternalPrincipalFilter.error(response, 403, "FORBIDDEN", "Access denied")))
                .addFilterAfter(new InternalPrincipalFilter(identities), BasicAuthenticationFilter.class);
        return http.build();
    }
}
