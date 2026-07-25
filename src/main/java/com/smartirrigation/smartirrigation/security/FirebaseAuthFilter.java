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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final UsuarioRepository usuarioRepository;

    public FirebaseAuthFilter(FirebaseAuth firebaseAuth, UsuarioRepository usuarioRepository) {
        this.firebaseAuth = firebaseAuth;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String cleanedUri = StringUtils.cleanPath(request.getRequestURI());
        if (DevicePaths.matches(cleanedUri)) {
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
            String tipo = usuarioRepository.findById(uid).map(Usuario::getTipo).orElse("");

            GrantedAuthority authority = new SimpleGrantedAuthority(
                    "Administrador".equals(tipo) ? "ROLE_ADMIN" : "ROLE_USER");

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
