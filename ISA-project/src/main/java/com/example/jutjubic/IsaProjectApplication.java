package com.example.jutjubic;

import com.example.jutjubic.config.RateLimitingFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IsaProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(IsaProjectApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterFilter() {
        FilterRegistrationBean<RateLimitingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RateLimitingFilter());
        registrationBean.addUrlPatterns("/api/auth.login");
        return registrationBean;
    }
}
