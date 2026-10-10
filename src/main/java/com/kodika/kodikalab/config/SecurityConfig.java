package com.kodika.kodikalab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodika.kodikalab.security.JwtAuthenticationFilter;
import com.kodika.kodikalab.security.JwtProperties;
import com.kodika.kodikalab.security.JwtService;
import com.kodika.kodikalab.security.RestAccessDeniedHandler;
import com.kodika.kodikalab.security.RestAuthenticationEntryPoint;
import com.kodika.kodikalab.users.UserService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Seguridad de la API: JWT sin estado (cabecera {@code Authorization: Bearer}), sin sesión HTTP ni cookies.
 *
 * <p>Esta clase concentra las reglas de rol para que se vean en un solo lugar. Los servicios conservan sus
 * verificaciones de pertenencia y propiedad (por ejemplo, "coach responsable del equipo"), que dependen de datos
 * y no del rol.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    private static final String COACH = "COACH";
    private static final String PRACTICANTE = "PRACTICANTE";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserService userService,
                                                   ObjectMapper objectMapper) throws Exception {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher publicEndpoints = new OrRequestMatcher(
                paths.matcher(HttpMethod.OPTIONS, "/**"),
                paths.matcher(HttpMethod.POST, "/auth/register"),
                paths.matcher(HttpMethod.POST, "/auth/login"),
                paths.matcher(HttpMethod.POST, "/auth/recovery"),
                paths.matcher("/swagger-ui.html"),
                paths.matcher("/swagger-ui/**"),
                paths.matcher("/v3/api-docs"),
                paths.matcher("/v3/api-docs/**"),
                paths.matcher("/error"));
        RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);

        return http
                .csrf(AbstractHttpConfigurer::disable) // sin cookies de sesión no hay CSRF que mitigar
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicEndpoints).permitAll()

                        // Solo COACH
                        .requestMatchers(HttpMethod.POST, "/teams").hasRole(COACH)                                // US-04
                        .requestMatchers(HttpMethod.GET, "/teams/{id}/memberships").hasRole(COACH)                // US-06
                        .requestMatchers(HttpMethod.PATCH, "/teams/{id}/memberships/{memberId}").hasRole(COACH)   // US-06
                        .requestMatchers(HttpMethod.POST, "/problems", "/problems/assign").hasRole(COACH)         // US-07
                        .requestMatchers(HttpMethod.POST, "/competitions").hasRole(COACH)                         // US-13
                        .requestMatchers("/competitions/{competitionId}/official-result").hasRole(COACH)          // US-13
                        .requestMatchers(HttpMethod.GET, "/competitions/teams/{teamId}/official-results")
                        .hasRole(COACH)                                                                           // US-13
                        .requestMatchers(HttpMethod.GET, "/analytics/teams/{teamId}/weaknesses").hasRole(COACH)   // US-12

                        // Solo PRACTICANTE
                        .requestMatchers(HttpMethod.POST, "/teams/{id}/join").hasRole(PRACTICANTE)                // US-05
                        .requestMatchers(HttpMethod.POST,
                                "/competitions/teams/{teamId}/problems/{competitionProblemId}/resolutions")
                        .hasRole(PRACTICANTE)                                                                     // US-09/14
                        .requestMatchers(HttpMethod.GET, "/analytics/teams/{teamId}/progress/me")
                        .hasRole(PRACTICANTE)                                                                     // US-14

                        // Cualquier cuenta autenticada: perfil, listado de grupos, catálogo, problemas asignados, ranking
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userService, accessDeniedHandler,
                        publicEndpoints), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
