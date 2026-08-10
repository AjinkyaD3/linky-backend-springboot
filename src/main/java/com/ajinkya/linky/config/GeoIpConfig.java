package com.ajinkya.linky.config;

import com.maxmind.geoip2.DatabaseReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import ua_parser.Parser;

import java.io.InputStream;

@Configuration
public class GeoIpConfig {

    private static final Logger log = LoggerFactory.getLogger(GeoIpConfig.class);

    @Bean
    public Parser uaParser() {
        return new Parser();
    }

    @Bean
    public DatabaseReader databaseReader() {
        try {
            ClassPathResource resource = new ClassPathResource("GeoLite2-City.mmdb");
            if (resource.exists()) {
                InputStream is = resource.getInputStream();
                return new DatabaseReader.Builder(is).build();
            } else {
                log.warn("GeoLite2-City.mmdb not found in classpath. GeoIP lookup will be disabled.");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to initialize GeoIP DatabaseReader", e);
            return null;
        }
    }
}
