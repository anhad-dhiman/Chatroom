package com.anhad.chatroom.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(JwtService.stripBearer(header)).ifPresent(user ->
                    SecurityContextHolder.getContext().setAuthentication(authentication(user)));
        }
        chain.doFilter(request, response);
    }

    public static UsernamePasswordAuthenticationToken authentication(AuthUser user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
