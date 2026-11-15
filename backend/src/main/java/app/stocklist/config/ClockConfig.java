package app.stocklist.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    /** All server timestamps come from this clock (UTC), never from the phone. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
