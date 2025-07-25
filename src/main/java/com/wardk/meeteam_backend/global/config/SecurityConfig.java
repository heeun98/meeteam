package com.wardk.meeteam_backend.global.config;

import com.wardk.meeteam_backend.global.filter.JwtFilter;
import com.wardk.meeteam_backend.global.filter.LoginFilter;
import com.wardk.meeteam_backend.global.loginRegister.handler.OAuth2AuthenticationSuccessHandler;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtUtil jwtUtil;
  private final AuthenticationConfiguration authenticationConfiguration;
  private final OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService;
  private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

  /**
   * Security Filter Chain 설정
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    // 로그인 경로를 설정하기 위해 LoginFilter 생성
    LoginFilter loginFilter = new LoginFilter(jwtUtil, authenticationManager(authenticationConfiguration));
    loginFilter.setFilterProcessesUrl("/api/login"); // TODO: 로그인 경로 커스텀 "/api/auth/login"
    //->경로를 커스텀 할 수 있다.
    return http
        // cors 설정
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // csrf disable
        .csrf(AbstractHttpConfigurer::disable)
        // http basic 인증 방식 disable
        .httpBasic(AbstractHttpConfigurer::disable)
        // form 로그인 방식 disable
        .formLogin(AbstractHttpConfigurer::disable)
        // 경로별 인가 작업
        .authorizeHttpRequests((authorize) -> authorize
            .requestMatchers("/actuator/**",
                    "/docs/**", "/api/register", "/v3/**",
                    "/api/login", "/api/community/**", "/api/**",
                    "/api/auth/**","/","/uploads/**", "/api/register", "api/project/register", "/oauth2/**", "/login/oauth2/**", "/login/oauth2/code/**",
                    "/api/auth/oauth2/success", "/api/auth/oauth2/failure").permitAll() // TODO: 인증 생략 경로 설정  회원가입: "/api/user/register", 로그인: "/api/auth/login"//
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated() //나머지는 인증이 된 사용자만 가능
        )
        // OAuth 2.0 로그인 설정
        .oauth2Login(oauth2 -> oauth2
                .successHandler(oauth2SuccessHandler) // OAuth 성공 후 핸들러 설정
                .failureUrl("/api/auth/oauth2/failure") // OAuth 실패 후 리다이렉트 URL
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService) // 커스텀 OAuth2UserService 사용
                )
        )
//            .authorizeHttpRequests(auth -> auth
//                        .requestMatchers("/api/auth/login", "/api/auth/join").permitAll() // 로그인, 회원가입은 누구나 접근 가능
//                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // /api/admin/** 경로는 ROLE_ADMIN만 접근 가능
//                        .anyRequest().authenticated()
        // 세션 설정 STATELESS 에서 세션 설정 수정 - OAuth2 사용시 IF_REQUIRED 필요
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        )
        .addFilterBefore(
            new JwtFilter(jwtUtil),
            LoginFilter.class
        )
        .addFilterAt(
            loginFilter,
            UsernamePasswordAuthenticationFilter.class
        )
        .build();
  }

  /**
   * 인증 메니저 설정
   */
  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration)
      throws Exception {

    return authenticationConfiguration.getAuthenticationManager();
  }

  /**
   * CORS 설정 소스 빈
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOriginPatterns(List.of("http://localhost:3000")); // 허용할 오리진 TODO: CORS 경로 설정 "http://localhost:3000"

    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")); // 허용할 HTTP 메서드
    configuration.setAllowCredentials(true); // 인증 정보 포함 여부
    configuration.setAllowedHeaders(Collections.singletonList("*")); // 허용할 헤더
    configuration.setMaxAge(3600L); // Preflight 캐싱 시간

    // 모든 경로에 대해 CORS 설정 적용
    UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
    urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", configuration);
    return urlBasedCorsConfigurationSource;
  }

  /**
   * 비밀번호 인코더 빈 (BCrypt)
   */
  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}