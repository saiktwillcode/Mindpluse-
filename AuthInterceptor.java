package com.mindpulse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.io.IOException;
import java.util.Set;

/** Requires a valid Bearer JWT on /api/** (except auth + health) and enforces roles. */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwt;
    public AuthInterceptor(JwtService jwt) { this.jwt = jwt; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        String path = req.getRequestURI();
        if ("OPTIONS".equals(req.getMethod()) || path.startsWith("/api/auth/") || path.equals("/api/health")) return true;
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return deny(res, 401, "Authentication required.");
        try {
            Claims c = jwt.parse(h.substring(7));
            String role = c.get("role", String.class);
            req.setAttribute("email", c.getSubject());
            req.setAttribute("role", role);
            if (path.startsWith("/api/dashboard/faculty") && !Set.of("FACULTY", "ADMIN").contains(role))
                return deny(res, 403, "Faculty access required.");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return deny(res, 401, "Session expired or invalid. Please sign in again.");
        }
    }

    private boolean deny(HttpServletResponse res, int code, String msg) throws IOException {
        res.setStatus(code);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
        return false;
    }
}
