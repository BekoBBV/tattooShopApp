package com.tattoostudio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;

@Configuration
public class MongoConfig {

    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(config -> config
                // Store prices as exact decimals (not strings or floating point)
                .bigDecimal(BigDecimalRepresentation.DECIMAL128)
                // Store LocalDate/LocalTime in UTC, so data reads the same on any machine's timezone
                .useNativeDriverJavaTimeCodecs());
    }
}
