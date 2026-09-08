package com.abhiroop.recall.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class RecallMcpAuthFilter extends OncePerRequestFilter {

    private final String recallApiKey;
    private final RequestMatcher mcpEndpointMatcher;

    public RecallMcpAuthFilter(String recallApiKey, RequestMatcher mcpEndpointMatcher) {
        this.recallApiKey = recallApiKey;
        this.mcpEndpointMatcher = mcpEndpointMatcher;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !mcpEndpointMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorizedResponse(response);
            return;
        }

        String token = authHeader.substring(7);
        if (!recallApiKey.equals(token)) {
            sendUnauthorizedResponse(response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mcp-client", null, List.of())
        );

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("text/plain");
        response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(HttpStatus.UNAUTHORIZED.toString());
    }
}
