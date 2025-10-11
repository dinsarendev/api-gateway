package com.cambofreelance.apigateway.configs;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import com.cambofreelance.apigateway.constants.ErrorCode;
import com.cambofreelance.apigateway.dto.BaseResponse;
import com.cambofreelance.apigateway.exception.ResponseEncryptionException;
import com.cambofreelance.apigateway.utils.AESCipherEncryption;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class EncryptResponseBodyFilter extends AbstractGatewayFilterFactory<EncryptResponseBodyFilter.Config> {
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBody;
    private final ObjectMapper objectMapper;
    private final AESCipherEncryption aesCipherEncryption;

    @Override
    public GatewayFilter apply(Config config) {
        final ModifyResponseBodyGatewayFilterFactory.Config modifyResponseBodyConfig = new ModifyResponseBodyGatewayFilterFactory.Config();
        modifyResponseBodyConfig.setRewriteFunction(String.class, String.class, (exchange, bodyAsString) -> {
            try {
                if(StringUtils.isEmpty(bodyAsString)) return Mono.empty();

                TypeReference<BaseResponse<Object>> typeRef = new TypeReference<>() {};
                BaseResponse<Object> responseData = objectMapper.readValue(bodyAsString, typeRef);

                if(responseData.isSuccess()){
                    String strData = objectMapper.writeValueAsString(responseData.getData());
                    String iv = AESCipherEncryption.generateIv();
                    String cipherText = aesCipherEncryption.encrypt(strData, iv);
                    responseData.setData(cipherText);
                    responseData.setIv(iv);
                }

                String encryptedResponse = objectMapper.writeValueAsString(responseData);
                return Mono.just(encryptedResponse);
            } catch (Throwable e) {
                log.error("Failed to encrypt response body {}", e.getMessage());
                // Option 1: Wrap your BaseResponse in a custom Exception
                BaseResponse<Object> responseData = new BaseResponse<>();
                responseData.setTimestamp(new Date().getTime());
                responseData.setData(null);
                responseData.setSuccess(Boolean.FALSE);
                responseData.setCode(ErrorCode.ERROR_DECRYPT_BODY);
                responseData.setMessage("Error encrypt body request");
                return Mono.error(new ResponseEncryptionException(responseData, e));
            }
        });

        return modifyResponseBody.apply(modifyResponseBodyConfig);
    }

    public static class Config{

    }

    public EncryptResponseBodyFilter(ModifyResponseBodyGatewayFilterFactory modifyResponseBody, ObjectMapper objectMapper, AESCipherEncryption aesCipherEncryption){
        super(Config.class);
        this.modifyResponseBody = modifyResponseBody;
        this.objectMapper = objectMapper;
        this.aesCipherEncryption = aesCipherEncryption;
    }
}