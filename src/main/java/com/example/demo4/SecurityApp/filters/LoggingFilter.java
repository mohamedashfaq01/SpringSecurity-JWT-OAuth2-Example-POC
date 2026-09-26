package com.example.demo4.SecurityApp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        // 1. Log Incoming Request
        log.info("Incoming Request: [Method: {}] [URI: {}] [Client IP: {}]",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

        // Process the rest of the filter chain
        filterChain.doFilter(request, response);

        long timeTaken = System.currentTimeMillis() - startTime;

        // 2. Log Outgoing Response
        log.info("Outgoing Response: [Status: {}] [URI: {}] [Time Taken: {}ms]",
                response.getStatus(), request.getRequestURI(), timeTaken);
    }
}
