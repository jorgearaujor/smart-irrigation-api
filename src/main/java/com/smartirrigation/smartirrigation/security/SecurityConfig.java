package com.smartirrigation.smartirrigation.security;

import com.google.firebase.auth.FirebaseAuth;
import com.smartirrigation.smartirrigation.config.FirebaseEnv;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, FirebaseAuth firebaseAuth,
                                                     UsuarioRepository usuarioRepository, FirebaseEnv firebaseEnv) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/webhooks/**", "/api/devices/*/config", "/api/devices/*/commands/poll").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new DeviceKeyFilter(firebaseEnv), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new FirebaseAuthFilter(firebaseAuth, usuarioRepository), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
