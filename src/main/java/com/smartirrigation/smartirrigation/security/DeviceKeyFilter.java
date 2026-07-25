package com.smartirrigation.smartirrigation.security;

import com.smartirrigation.smartirrigation.config.FirebaseEnv;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class DeviceKeyFilter extends OncePerRequestFilter {

    private final FirebaseEnv firebaseEnv;

    public DeviceKeyFilter(FirebaseEnv firebaseEnv) {
        this.firebaseEnv = firebaseEnv;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String cleanedUri = StringUtils.cleanPath(request.getRequestURI());
        if (!DevicePaths.matches(cleanedUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("X-Device-Key");
        if (key == null || !key.equals(firebaseEnv.deviceApiKey())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-Device-Key ausente ou invalida");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
