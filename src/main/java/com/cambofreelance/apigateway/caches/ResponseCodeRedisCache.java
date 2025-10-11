package com.cambofreelance.apigateway.caches;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.List;
import com.cambofreelance.apigateway.dto.ResponseCodeDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResponseCodeRedisCache {

  @Value("${storage.redis.key-response-code}")
  private String keyValue;

  private String key;

  private HashOperations<String, String, ResponseCodeDto> hashOperations;

  @Resource
  private RedisTemplate<String, ResponseCodeDto> responseCodeRedisTemplate;

  @PostConstruct
  private void init() {
    this.key = keyValue;
    this.hashOperations = responseCodeRedisTemplate.opsForHash();
  }

  public ResponseCodeDto getRespCode(final String code) {
    return hashOperations.get(key, code);
  }

  public void initRespCodeCache(List<ResponseCodeDto> respCodes) {
    responseCodeRedisTemplate.delete(key); // clear existing cache
    for (ResponseCodeDto responseCode : respCodes) {
      hashOperations.put(key, responseCode.getCode(), responseCode);
    }
  }

  public void addRespCodeCache(final ResponseCodeDto respCode) {
    hashOperations.put(key, respCode.getCode(), respCode);
  }

  public void reloadRespCode(final ResponseCodeDto respCode) {
    hashOperations.put(key, respCode.getCode(), respCode);
  }

  public String getRespMessage(final String code) {
    ResponseCodeDto dto = hashOperations.get(key, code);
    return dto != null ? dto.getMessage() : null;
  }
}
