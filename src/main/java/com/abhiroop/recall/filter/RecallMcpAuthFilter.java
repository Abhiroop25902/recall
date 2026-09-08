package com.abhiroop.recall.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class RecallMcpAuthFilter extends OncePerRequestFilter {

    private final @Nullable String recallApiKey;
    private final RequestMatcher mcpEndpointMatcher;

    public RecallMcpAuthFilter(@Nullable String recallApiKey, RequestMatcher mcpEndpointMatcher) {
        this.recallApiKey = recallApiKey;
        this.mcpEndpointMatcher = mcpEndpointMatcher;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !mcpEndpointMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (recallApiKey == null) {
            log.error("${sm@RECALL_API_KEY} did not yield any value; Check Google Cloud Secrets");

            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.getWriter().write(HttpStatus.INTERNAL_SERVER_ERROR.toString());
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(response, "Missing or malformed Authorization header.");
            return;
        }

        String token = authHeader.substring(7);
        if (!recallApiKey.equals(token)) {
            sendErrorResponse(response, "Invalid MCP token credentials.");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mcp-client", null, List.of())
        );

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
    }
}
