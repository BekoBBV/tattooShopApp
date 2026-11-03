package com.tattoostudio.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** App-wide setup: the clock, background jobs (scheduling + async) and typed settings. */
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({ BookingRules.class, ShopInfo.class })
public class AppConfig {

    /** All "now" checks go through this clock, so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
