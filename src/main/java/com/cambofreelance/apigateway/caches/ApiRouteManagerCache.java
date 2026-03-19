package com.cambofreelance.apigateway.caches;

import java.util.Hashtable;
import java.util.List;

import com.cambofreelance.apigateway.dto.ApiRouteDto;
import org.springframework.util.AntPathMatcher;

public class ApiRouteManagerCache {

    private static Hashtable<String, ApiRouteDto> apiRouteCache;
    private static Hashtable<String, String> generalCaches;

    private static final AntPathMatcher matcher = new AntPathMatcher();

    public static void init(List<ApiRouteDto> apiRouteDtoList) {
        generalCaches = new Hashtable<>();
        apiRouteCache = new Hashtable<>();

        if (apiRouteDtoList != null && !apiRouteDtoList.isEmpty()) {
            for (ApiRouteDto value : apiRouteDtoList) {
                // key = path:method  (IMPORTANT)
                String key = buildKey(value.getPath(), value.getMethod());
                apiRouteCache.put(key, value);
            }
        }
    }

    // ================= GET (NEW - SUPPORT METHOD + WILDCARD) =================
    public static ApiRouteDto get(String path, String method) {

        if (apiRouteCache == null) return null;

        // 1. Exact match (FAST)
        String key = buildKey(path, method);
        ApiRouteDto exact = apiRouteCache.get(key);

        if (exact != null) {
            return exact;
        }

        // 2. Wildcard match (/**, *, etc.)
        return apiRouteCache.values().stream()
            .filter(dto -> dto.getMethod().equalsIgnoreCase(method))
            .filter(dto -> matcher.match(dto.getPath(), path))
            // IMPORTANT: most specific path first
            .sorted((a, b) -> b.getPath().length() - a.getPath().length())
            .findFirst()
            .orElse(null);
    }

    // ================= LEGACY (KEEP IF NEEDED) =================
    public static ApiRouteDto getByPath(String path) {
        if (apiRouteCache == null) return null;

        return apiRouteCache.values().stream()
            .filter(dto -> dto.getPath().equals(path))
            .findFirst()
            .orElse(null);
    }

    // ================= GENERAL CACHE =================
    public static String getGeneral(String key) {
        return generalCaches.get(key);
    }

    public static void addGeneral(String key, String value) {
        generalCaches.put(key, value);
    }

    // ================= UTIL =================
    private static String buildKey(String path, String method) {
        return path + ":" + method.toUpperCase();
    }
}