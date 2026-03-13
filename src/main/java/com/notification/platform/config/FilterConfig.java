package com.notification.platform.config;

import com.notification.platform.api.filter.ApiKeyAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Value("${platform.api-key}")
    private String apiKey;

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyFilter() {
        FilterRegistrationBean<ApiKeyAuthFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ApiKeyAuthFilter(apiKey));
        // Only protect the business API endpoints, leave Actuator and WebSockets open or handled separately
        registrationBean.addUrlPatterns("/v1/notifications", "/v1/notifications/*"); 
        return registrationBean;
    }
}
