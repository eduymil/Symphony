package com.ledger.auth;

import com.ledger.account.AppUser;
import com.ledger.account.AppUserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Simple authentication filter that checks for X-Username header.
 * Skips authentication for login endpoint, Swagger UI, and OPTIONS requests.
 */
@Component
public class AuthFilter implements Filter {

    private final AppUserRepository userRepository;

    public AuthFilter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Skip auth for these paths
        if (method.equals("OPTIONS") ||
            path.startsWith("/api/auth/login") ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/api/auth/logout")) {
            chain.doFilter(request, response);
            return;
        }

        String username = httpRequest.getHeader("X-Username");
        if (username == null || username.isBlank()) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Missing X-Username header. Please log in.\"}");
            return;
        }

        Optional<AppUser> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Invalid user: " + username + "\"}");
            return;
        }

        // Store user in request attribute for controllers to access
        httpRequest.setAttribute("authenticatedUser", user.get());
        chain.doFilter(request, response);
    }
}
