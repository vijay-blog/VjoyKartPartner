package com.nexamart.backend.config;

import com.nexamart.backend.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
  @Bean AuthenticationEntryPoint authenticationEntryPoint(){return (request,response,exception)->response.sendError(401);}
  @Bean AccessDeniedHandler accessDeniedHandler(){return (request,response,exception)->response.sendError(403);}
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt) throws Exception {
    http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a->a
        .requestMatchers(HttpMethod.GET,"/api/v1/health","/api/v1/catalog/**").permitAll()
        .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/admin/login","/api/v1/auth/refresh","/api/v1/auth/partner/send-otp","/api/v1/auth/partner/verify-otp","/api/v1/auth/partner/register").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/v1/delivery/**").hasRole("DELIVERY_PARTNER")
        .anyRequest().authenticated())
      .exceptionHandling(e->e.authenticationEntryPoint(authenticationEntryPoint()).accessDeniedHandler(accessDeniedHandler()))
      .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
