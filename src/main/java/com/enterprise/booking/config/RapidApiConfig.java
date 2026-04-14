package com.enterprise.booking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "preview.provider", havingValue = "rapidapi")
@EnableConfigurationProperties(RapidApiProperties.class)
public class RapidApiConfig {

    private static final Logger log = LoggerFactory.getLogger(RapidApiConfig.class);

    @Bean
    public RestClient rapidApiRestClient(RestClient.Builder builder, RapidApiProperties properties) {
        log.info("rapidApiRestClient start baseUrl={} host={} connectTimeoutMs={} readTimeoutMs={}",
                properties.getBaseUrl(), properties.getHost(), properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            log.error("rapidApiRestClient missing api key");
            throw new IllegalStateException("Missing RapidAPI key. Set booking.rapidapi.key.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());

        RestClient client = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-rapidapi-host", properties.getHost())
                .defaultHeader("x-rapidapi-key", properties.getKey())
                .requestFactory(requestFactory)
                .build();
        log.info("rapidApiRestClient done");
        return client;
    }
}
