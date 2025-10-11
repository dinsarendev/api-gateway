package com.cambofreelance.apigateway;

import jakarta.annotation.PostConstruct;
import com.cambofreelance.apigateway.startup.Startup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class ApiGatewayApplication {

    private final Startup startup;

    public ApiGatewayApplication(Startup startup) {
        this.startup = startup;
    }

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @PostConstruct
    private void init() {
        log.info("Initializing api route Manager ...");
        startup.initApiMigrate();
        log.info("Finished api route Manager ...");
        log.error("DEPLOYMENT ONE");
    }

}
