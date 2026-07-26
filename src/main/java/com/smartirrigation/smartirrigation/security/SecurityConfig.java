package com.smartirrigation.smartirrigation.security;

import com.google.firebase.auth.FirebaseAuth;
import com.smartirrigation.smartirrigation.config.FirebaseEnv;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, FirebaseAuth firebaseAuth,
                                                     UsuarioRepository usuarioRepository, FirebaseEnv firebaseEnv) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(DevicePaths.PATTERNS.toArray(new String[0])).permitAll()
                            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                            // Must stay public: our filters call response.sendError(...), which triggers an
                            // internal forward to /error that re-enters this same filter chain. Without this,
                            // that forwarded request gets denied by AuthorizationFilter (no principal on an
                            // error dispatch), masking the original 401/403 with an empty 403 from
                            // Http403ForbiddenEntryPoint.
                            .requestMatchers("/error").permitAll();

                    // Web dashboard login is implemented but switched off for now (see FirebaseEnv);
                    // set DASHBOARD_AUTH_ENABLED=true to require authentication again.
                    if (firebaseEnv.dashboardAuthEnabled()) {
                        auth.anyRequest().authenticated();
                    } else {
                        auth.anyRequest().permitAll();
                    }
                })
                .addFilterBefore(new DeviceKeyFilter(firebaseEnv), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new FirebaseAuthFilter(firebaseAuth, usuarioRepository, firebaseEnv.dashboardAuthEnabled()),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
