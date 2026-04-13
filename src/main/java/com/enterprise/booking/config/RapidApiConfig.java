package com.enterprise.booking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "preview.provider", havingValue = "rapidapi")
@EnableConfigurationProperties(RapidApiProperties.class)
public class RapidApiConfig {

    @Bean
    public RestClient rapidApiRestClient(RestClient.Builder builder, RapidApiProperties properties) {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new IllegalStateException("Missing RapidAPI key. Set booking.rapidapi.key.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());

        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-rapidapi-host", properties.getHost())
                .defaultHeader("x-rapidapi-key", properties.getKey())
                .requestFactory(requestFactory)
                .build();
    }
}
