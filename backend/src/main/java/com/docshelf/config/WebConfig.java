// Web layer wiring: API token filter registration for /api/*, permissive CORS (the token is the control)
package com.docshelf.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<ApiTokenFilter> apiTokenFilterRegistration(DocshelfProperties props,
                                                                             ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiTokenFilter> registration =
                new FilterRegistrationBean<>(new ApiTokenFilter(props.apiToken(), objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setName("apiTokenFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "Location")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
