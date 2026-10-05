package com.societycentral.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the server-local clock used for authoritative event scheduling rules.
 */
@Configuration
public class ClockConfig {

    /**
     * Creates the production clock in the server's configured local time zone.
     *
     * @return the application clock
     */
    @Bean
    public Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
