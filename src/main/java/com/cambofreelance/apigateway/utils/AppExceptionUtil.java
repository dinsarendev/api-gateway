package com.cambofreelance.apigateway.utils;

import java.util.Objects;
import com.cambofreelance.apigateway.caches.ResponseCodeRedisCache;
import com.cambofreelance.apigateway.dto.ResponseCodeDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AppExceptionUtil {

    private static ResponseCodeRedisCache responseCodeRedisCache;

    @Autowired
    public AppExceptionUtil(ResponseCodeRedisCache cache) {
        AppExceptionUtil.responseCodeRedisCache = cache;
    }

    public static ResponseCodeDto buildMessage(String code) {
        ResponseCodeDto responseCodeDto = new ResponseCodeDto();
        responseCodeDto.getDefault();
        try {
            return Objects.isNull(responseCodeRedisCache.getRespCode(code)) ? responseCodeDto
                    : responseCodeRedisCache.getRespCode(code);
        } catch (Throwable e) {
            log.info("Error get response code from redis {}", e.getMessage());
        }
        return responseCodeDto;
    }
}
