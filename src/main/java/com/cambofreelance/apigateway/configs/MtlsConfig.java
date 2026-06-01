package com.cambofreelance.apigateway.configs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mTLS (Mutual TLS) configuration.
 *
 * Enable via application.yml:
 *
 *   server:
 *     ssl:
 *       enabled: true
 *       client-auth: need          # none | want | need
 *       key-store: classpath:ssl/keystore.p12
 *       key-store-password: ${SSL_KEY_STORE_PASSWORD}
 *       key-store-type: PKCS12
 *       trust-store: classpath:ssl/truststore.p12
 *       trust-store-password: ${SSL_TRUST_STORE_PASSWORD}
 *
 * When mTLS is active, the filter chain extracts the client certificate CN
 * and forwards it upstream as the X-Client-Cert-CN header.
 *
 * Key/truststore generation (dev):
 *   keytool -genkeypair -alias gateway -keyalg RSA -keysize 2048 \
 *     -storetype PKCS12 -keystore keystore.p12 -validity 3650
 *   keytool -export -alias gateway -keystore keystore.p12 \
 *     -rfc -file gateway.crt
 *   keytool -import -alias gateway -file gateway.crt \
 *     -keystore truststore.p12 -storetype PKCS12
 */
@Slf4j
@Configuration
public class MtlsConfig {

    @Bean
    @ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> mtlsCustomizer() {
        return factory -> log.info("mTLS enabled — client-auth mode configured via server.ssl.client-auth");
    }
}