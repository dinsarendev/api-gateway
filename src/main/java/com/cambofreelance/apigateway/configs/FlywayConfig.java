package com.cambofreelance.apigateway.configs;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    @Value("${spring.r2dbc.username}")
    private String username;

    @Value("${spring.r2dbc.password}")
    private String password;

    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        // r2dbc:postgresql:// or r2dbc:pool:postgresql:// → jdbc:postgresql://
        String jdbcUrl = r2dbcUrl
                .replaceFirst("^r2dbc:pool:postgresql", "jdbc:postgresql")
                .replaceFirst("^r2dbc:postgresql",       "jdbc:postgresql");

        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}
