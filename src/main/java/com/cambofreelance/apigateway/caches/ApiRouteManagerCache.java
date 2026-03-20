package com.cambofreelance.apigateway.caches;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.cambofreelance.apigateway.dto.ApiRouteDto;

public class ApiRouteManagerCache {

    // ================= CACHE =================
    private static Map<String, ApiRouteDto> exactMap;
    private static List<ApiRouteDto> wildcardList;
    private static Map<String, String> generalCaches;

    // ================= INIT =================
    public static void init(List<ApiRouteDto> routes) {

        exactMap = new ConcurrentHashMap<>();
        wildcardList = new ArrayList<>();
        generalCaches = new ConcurrentHashMap<>();

        if (routes == null || routes.isEmpty()) return;

        for (ApiRouteDto route : routes) {

            String path = normalize(route.getPath());
            route.setPath(path);

            String key = buildKey(path, route.getMethod());

            if (isWildcard(path)) {

                // precompute basePath
                route.setPath(extractBasePath(path));

                wildcardList.add(route);

            } else {
                exactMap.put(key, route);
            }
        }

        // sort once (IMPORTANT)
        wildcardList.sort((a, b) ->
            b.getPath().length() - a.getPath().length()
        );
    }

    // ================= GET =================
    public static ApiRouteDto get(String path, String method) {

        if (exactMap == null) return null;

        String normalizedPath = normalize(path);
        String key = buildKey(normalizedPath, method);

        // 1. Exact match (O(1))
        ApiRouteDto exact = exactMap.get(key);
        if (exact != null) {
            return exact;
        }

        // 2. Wildcard match (FAST)
        for (ApiRouteDto dto : wildcardList) {

            if (!dto.getMethod().equalsIgnoreCase(method)) continue;

            if (normalizedPath.startsWith(dto.getPath())) {
                return dto;
            }
        }

        return null;
    }

    // ================= GENERAL CACHE =================
    public static String getGeneral(String key) {
        return generalCaches.get(key);
    }

    public static void addGeneral(String key, String value) {
        generalCaches.put(key, value);
    }

    // ================= UTIL =================
    private static boolean isWildcard(String path) {
        return path.endsWith("/**");
    }

    private static String extractBasePath(String path) {
        return path.substring(0, path.length() - 3);
    }

    private static String buildKey(String path, String method) {
        return path + ":" + method.toUpperCase();
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) return "/";

        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }

        return path;
    }
}