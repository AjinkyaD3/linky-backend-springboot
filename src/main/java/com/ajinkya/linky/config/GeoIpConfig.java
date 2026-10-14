package com.ajinkya.linky.config;

import com.maxmind.geoip2.DatabaseReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import ua_parser.Parser;

import java.io.File;

@Configuration
public class GeoIpConfig {

    private static final Logger log = LoggerFactory.getLogger(GeoIpConfig.class);

    @Bean
    public Parser uaParser() {
        return new Parser();
    }

    /**
     * Loads a MaxMind-format city database (MaxMind GeoLite2-City or the free DB-IP City Lite, see
     * scripts/download-geoip.sh) from GEOIP_DB_PATH, or from the classpath as GeoLite2-City.mmdb.
     * Returns null (geo analytics disabled, everything else works) when no database is found.
     */
    @Bean
    public DatabaseReader databaseReader(@Value("${app.geoip.db-path:}") String dbPath) {
        try {
            if (dbPath != null && !dbPath.isBlank()) {
                File file = new File(dbPath);
                if (!file.isFile()) {
                    log.warn("GEOIP_DB_PATH={} does not exist. GeoIP lookup will be disabled.", dbPath);
                    return null;
                }
                DatabaseReader reader = new DatabaseReader.Builder(file).build();
                log.info("GeoIP enabled using {} ({})", dbPath, reader.getMetadata().getDatabaseType());
                return reader;
            }
            ClassPathResource resource = new ClassPathResource("GeoLite2-City.mmdb");
            if (resource.exists()) {
                log.info("GeoIP enabled using classpath GeoLite2-City.mmdb");
                return new DatabaseReader.Builder(resource.getInputStream()).build();
            }
            log.warn("No GeoIP database configured (set GEOIP_DB_PATH). Country/city analytics will show Unknown.");
            return null;
        } catch (Exception e) {
            log.error("Failed to initialize GeoIP DatabaseReader", e);
            return null;
        }
    }
}
