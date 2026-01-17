package cafe.shigure.ShigureCafeBackened.config;

import cafe.shigure.ShigureCafeBackened.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/token", "/api/v1/auth/2fa/verify", "/api/v1/auth/verification-codes", "/api/v1/auth/password-reset").permitAll()
                .requestMatchers("/api/v1/minecraft/whitelist").hasAnyAuthority("ADMIN", "API_CLIENT")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/minecraft/chat").hasAnyAuthority("ADMIN", "API_CLIENT")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/registrations").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/registrations").hasAuthority("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/registrations/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/registrations/**").hasAuthority("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/registrations/**").hasAuthority("ADMIN")
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
