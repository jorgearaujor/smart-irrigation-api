package com.smartirrigation.smartirrigation.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.smartirrigation.smartirrigation.model.Usuario;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class FirebaseAuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final FirebaseAuth firebaseAuth;
    private final UsuarioRepository usuarioRepository;
    private final boolean dashboardAuthEnabled;

    public FirebaseAuthFilter(FirebaseAuth firebaseAuth, UsuarioRepository usuarioRepository,
                               boolean dashboardAuthEnabled) {
        this.firebaseAuth = firebaseAuth;
        this.usuarioRepository = usuarioRepository;
        this.dashboardAuthEnabled = dashboardAuthEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String cleanedUri = StringUtils.cleanPath(request.getRequestURI());
        if (DevicePaths.matches(request.getMethod(), cleanedUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, cleanedUri))) {
            filterChain.doFilter(request, response);
            return;
        }

        // Web dashboard login is implemented but switched off for now (see FirebaseEnv).
        if (!dashboardAuthEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token de autenticacao ausente");
            return;
        }

        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(header.substring("Bearer ".length()));
            String uid = token.getUid();
            Usuario usuario = usuarioRepository.findById(uid).orElse(null);
            if (usuario == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Usuario nao autorizado");
                return;
            }

            GrantedAuthority authority = new SimpleGrantedAuthority(
                    "Administrador".equals(usuario.getTipo()) ? "ROLE_ADMIN" : "ROLE_USER");

            var authentication = new UsernamePasswordAuthenticationToken(uid, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token de autenticacao invalido");
        }
    }
}
