package com.brixo.slidehub.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad de ui-service (CLAUDE.md §11).
 *
 * Fase 0: configuración base con rutas públicas y protegidas por rol.
 * Fase 1 añadirá OAuth2 (GitHub, Google) y la persistencia de usuarios.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Vistas públicas (HU-005, HU-011, HU-012, HU-013)
                        .requestMatchers("/slides", "/remote", "/demo", "/showcase").permitAll()
                        // Autenticación pública
                        .requestMatchers("/auth/**").permitAll()
                        // Assets estáticos de slides
                        .requestMatchers("/presentation/**").permitAll()
                        // Polling de dispositivos (pasa por gateway, llega como /api/**)
                        .requestMatchers("/api/**").permitAll()
                        // Recursos estáticos (CSS, JS, imágenes)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/slides/**").permitAll()
                        // Panel del presentador y main panel — requiere PRESENTER o ADMIN
                        .requestMatchers("/presenter", "/main-panel").hasAnyRole("PRESENTER", "ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .defaultSuccessUrl("/presenter", true)
                        .failureUrl("/auth/login?error=true")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .invalidateHttpSession(true));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
