package com.booklane.booking.security;

import com.booklane.booking.adapters.http.ApiError;
import com.booklane.booking.domain.Actor;
import com.booklane.contracts.WireJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class InternalPrincipalFilter extends OncePerRequestFilter {
    public static final String ACTOR_ATTRIBUTE = "com.booklane.booking.internal.actor";
    private final DemoIdentityCatalog identities;

    public InternalPrincipalFilter(DemoIdentityCatalog identities) { this.identities = identities; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authenticated = SecurityContextHolder.getContext().getAuthentication();
        if (authenticated == null || !authenticated.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > 16384 || request.getHeader("Transfer-Encoding") != null) {
            error(response, 413, "PAYLOAD_TOO_LARGE", "Request body is too large or has unknown length");
            return;
        }
        var ids = Collections.list(request.getHeaders("X-Actor-Id"));
        var roles = Collections.list(request.getHeaders("X-Actor-Role"));
        try {
            if (ids.size() != 1 || roles.size() != 1) throw new IllegalArgumentException();
            UUID id = UUID.fromString(ids.getFirst());
            if (!id.toString().equals(ids.getFirst())) throw new IllegalArgumentException();
            var role = Actor.Role.valueOf(roles.getFirst());
            if (identities.findById(id).filter(identity -> identity.role().name().equals(role.name())).isEmpty()) {
                throw new IllegalArgumentException();
            }
            request.setAttribute(ACTOR_ATTRIBUTE, new Actor(id, role));
        } catch (IllegalArgumentException exception) {
            error(response, 403, "FORBIDDEN", "Invalid internal principal");
            return;
        }
        chain.doFilter(request, response);
    }

    public static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(WireJson.write(new ApiError(code, message)));
    }
}
