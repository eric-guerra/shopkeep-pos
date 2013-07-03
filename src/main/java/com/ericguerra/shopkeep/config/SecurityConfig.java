package com.ericguerra.shopkeep.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Two roles: CASHIER rings up sales and returns; MANAGER also edits the catalog, adjusts stock,
 * voids sales and sees reports. AngularJS reads the XSRF-TOKEN cookie and echoes it in X-XSRF-TOKEN,
 * which is exactly what CookieCsrfTokenRepository expects.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/app/**", "/css/**", "/login.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").hasAnyRole("CASHIER", "MANAGER")
                .requestMatchers("/api/products/**", "/api/inventory/**", "/api/reports/**").hasRole("MANAGER")
                .requestMatchers("/api/**").hasAnyRole("CASHIER", "MANAGER")
                .anyRequest().authenticated())
            // API calls get a plain 401 (the Angular interceptor redirects); pages get the login form.
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
            .httpBasic(basic -> {})
            .formLogin(form -> form.defaultSuccessUrl("/", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Demo users; swap for a JDBC UserDetailsService in production. */
    @Bean
    UserDetailsService users(PasswordEncoder encoder,
                             @Value("${shopkeep.users.cashier-password:cashier}") String cashierPw,
                             @Value("${shopkeep.users.manager-password:manager}") String managerPw) {
        return new InMemoryUserDetailsManager(
                User.withUsername("cashier").password(encoder.encode(cashierPw)).roles("CASHIER").build(),
                User.withUsername("manager").password(encoder.encode(managerPw)).roles("MANAGER").build());
    }
}
