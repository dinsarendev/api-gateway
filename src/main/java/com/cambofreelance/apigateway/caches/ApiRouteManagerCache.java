package com.cambofreelance.apigateway.caches;

import java.util.Hashtable;
import java.util.List;
import com.cambofreelance.apigateway.dto.ApiRouteDto;

public class ApiRouteManagerCache {
    private static Hashtable<String, ApiRouteDto> apiRouteCache;
    private static Hashtable<String, String> generalCaches;

    public static void init(List<ApiRouteDto> apiRouteDtoList) {
        generalCaches = new Hashtable<>();
        if (!apiRouteDtoList.isEmpty()) {
            apiRouteCache = new Hashtable<>();
            apiRouteDtoList.forEach(value -> apiRouteCache.put(value.getPath(), value));
        }
    }

    public static ApiRouteDto getByPath(String path) {
        return apiRouteCache.get(path);
    }

    public static String get(String key) {
        return generalCaches.get(key);
    }

    public static void add(String key, String value) {
        generalCaches.put(key, value);
    }
}

