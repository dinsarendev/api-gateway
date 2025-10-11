package com.cambofreelance.apigateway.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cambofreelance.apigateway.dto.BaseRequest;
import com.cambofreelance.apigateway.utils.AESCipherEncryption;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class DecryptRequestBodyFilter extends AbstractGatewayFilterFactory<DecryptRequestBodyFilter.Config> {
    private final ModifyRequestBodyGatewayFilterFactory modifyRequestBody;
    private final ObjectMapper objectMapper;
    private final AESCipherEncryption aesCipherEncryption;

    @Override
    public GatewayFilter apply(DecryptRequestBodyFilter.Config config) {
        final ModifyRequestBodyGatewayFilterFactory.Config modifyRequestBodyConfig = new ModifyRequestBodyGatewayFilterFactory.Config();

        modifyRequestBodyConfig.setRewriteFunction(String.class, String.class, (exchange, bodyAsString) -> {
            try {
                if(StringUtils.isEmpty(bodyAsString)) return Mono.empty();

                BaseRequest baseRequest = objectMapper.readValue(bodyAsString, BaseRequest.class);
                String decryptedResponse = aesCipherEncryption.decrypt(baseRequest.getPayload(), baseRequest.getIv());
                return Mono.just(decryptedResponse);
            } catch (Exception e) {
                log.error("Failed to decrypt request body {}", e.getMessage());
                return Mono.just(bodyAsString);
            }
        });

        return modifyRequestBody.apply(modifyRequestBodyConfig);
    }

    public static class Config{

    }

    public DecryptRequestBodyFilter(ModifyRequestBodyGatewayFilterFactory modifyRequestBody,
        ObjectMapper objectMapper, AESCipherEncryption aesCipherEncryption){
        super(DecryptRequestBodyFilter.Config.class);
        this.modifyRequestBody = modifyRequestBody;
        this.objectMapper = objectMapper;
        this.aesCipherEncryption = aesCipherEncryption;
    }
}