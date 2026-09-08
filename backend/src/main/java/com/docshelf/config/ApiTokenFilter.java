// Static bearer-token gate for every /api/* request (Authorization: Bearer <token> or X-Api-Token header)
package com.docshelf.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_API_TOKEN = "X-Api-Token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public ApiTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS preflight carries no credentials; the real request is checked.
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = extractToken(request);
        if (presented != null && constantTimeEquals(presented.getBytes(StandardCharsets.UTF_8), expectedToken)) {
            chain.doFilter(request, response);
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Missing or invalid API token");
        problem.setType(URI.create("https://docshelf.local/errors/unauthorized"));
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"docshelf\"");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private static String extractToken(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        String header = request.getHeader(HEADER_API_TOKEN);
        return header == null ? null : header.trim();
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
