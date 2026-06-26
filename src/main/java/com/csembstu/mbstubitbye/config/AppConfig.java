//package com.csembstu.mbstubitbye.config;
//
//
//import lombok.Getter;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.client.RestTemplate;
//
//@Getter
//@Configuration
//public class AppConfig {
//
//    @Value("${anthropic.api.key}")
//    private String apiKey;
//
//    @Value("${anthropic.api.url}")
//    private String apiUrl;
//
//    @Value("${anthropic.api.model}")
//    private String model;
//
//    @Value("${anthropic.api.max-tokens}")
//    private int maxTokens;
//
//    @Bean
//    public RestTemplate restTemplate() {
//        return new RestTemplate();
//    }
//
//}