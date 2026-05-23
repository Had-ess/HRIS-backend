package com.hris.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditContextFilter extends OncePerRequestFilter {

    public static final String MDC_CLIENT_IP = "clientIp";

    private static final String[] FORWARDED_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        if (clientIp != null && !clientIp.isBlank()) {
            MDC.put(MDC_CLIENT_IP, clientIp);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CLIENT_IP);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        for (String header : FORWARDED_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                int comma = value.indexOf(',');
                return (comma > 0 ? value.substring(0, comma) : value).trim();
            }
        }
        return request.getRemoteAddr();
    }
}
