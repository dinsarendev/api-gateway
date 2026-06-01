package com.cambofreelance.apigateway.caches;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.cambofreelance.apigateway.dto.ApiRouteDto;

public class ApiRouteManagerCache {

    // ================= CACHE =================
    private static Map<String, ApiRouteDto> exactMap;
    private static List<ApiRouteDto> pathVariableList;
    private static List<ApiRouteDto> wildcardList;
    private static Map<String, String> generalCaches;

    // ================= INIT =================
    public static void init(List<ApiRouteDto> routes) {

        exactMap = new ConcurrentHashMap<>();
        pathVariableList = new ArrayList<>();
        wildcardList = new ArrayList<>();
        generalCaches = new ConcurrentHashMap<>();

        if (routes == null || routes.isEmpty()) return;

        for (ApiRouteDto route : routes) {

            String path = normalize(route.getPath());
            route.setPath(path);

            String key = buildKey(path, route.getMethod());

            if (hasPathVariable(path)) {
                // path variable route: /users/{id}, /users/{id}/posts/{postId}, /users/{id}/**
                pathVariableList.add(route);

            } else if (isWildcard(path)) {
                // pure wildcard route: /api/**, /users/**
                route.setPath(extractBasePath(path));
                wildcardList.add(route);

            } else {
                exactMap.put(key, route);
            }
        }

        // sort path variable routes: most literal segments first (most specific match wins)
        pathVariableList.sort((a, b) ->
            countLiteralSegments(b.getPath()) - countLiteralSegments(a.getPath())
        );

        // sort wildcard routes: longest prefix first
        wildcardList.sort((a, b) ->
            b.getPath().length() - a.getPath().length()
        );
    }

    // ================= GET =================
    public static ApiRouteDto get(String path, String method) {

        if (exactMap == null) return null;

        String normalizedPath = normalize(path);
        String key = buildKey(normalizedPath, method);

        // 1. Exact match O(1)
        ApiRouteDto exact = exactMap.get(key);
        if (exact != null) {
            return exact;
        }

        // 2. Path variable match: /users/{id}, /items/{id}/details
        for (ApiRouteDto dto : pathVariableList) {
            if (!dto.getMethod().equalsIgnoreCase(method)) continue;
            if (matchesPathVariable(dto.getPath(), normalizedPath)) {
                return dto;
            }
        }

        // 3. Wildcard match: /api/**
        for (ApiRouteDto dto : wildcardList) {
            if (!dto.getMethod().equalsIgnoreCase(method)) continue;
            if (normalizedPath.equals(dto.getPath()) || normalizedPath.startsWith(dto.getPath() + "/")) {
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
    private static boolean hasPathVariable(String path) {
        return path != null && path.contains("{");
    }

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

    /**
     * Matches a request path against a path variable pattern.
     * Segments wrapped in {curly braces} match any single path segment.
     * A ** segment matches all remaining segments.
     *
     * Examples:
     *   /users/{id}            vs /users/123          → true
     *   /users/{id}/posts      vs /users/123/posts     → true
     *   /users/{id}/posts/**   vs /users/123/posts/1   → true
     *   /users/{id}            vs /users/123/extra     → false
     */
    private static boolean matchesPathVariable(String pattern, String requestPath) {
        String[] patternSegs = pattern.split("/", -1);
        String[] requestSegs = requestPath.split("/", -1);

        for (int i = 0; i < patternSegs.length; i++) {
            String ps = patternSegs[i];
            if ("**".equals(ps)) return true;
            if (i >= requestSegs.length) return false;
            if (ps.startsWith("{") && ps.endsWith("}")) continue;
            if (!ps.equals(requestSegs[i])) return false;
        }

        return patternSegs.length == requestSegs.length;
    }

    private static int countLiteralSegments(String path) {
        int count = 0;
        for (String seg : path.split("/", -1)) {
            if (!seg.isEmpty() && !seg.startsWith("{") && !"**".equals(seg)) {
                count++;
            }
        }
        return count;
    }
}