package com.cambofreelance.apigateway.caches;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.cambofreelance.apigateway.dto.ApiRouteDto;

public class ApiRouteManagerCache {

    // Volatile holder for atomic reload — swapped as a single reference so
    // concurrent reads never see a half-built state.
    private static volatile RouteHolder holder = RouteHolder.empty();

    // General-purpose key-value store (unrelated to route reload lifecycle).
    private static final Map<String, String> generalCaches = new ConcurrentHashMap<>();

    private record RouteHolder(
        Map<String, ApiRouteDto> exactMap,
        List<ApiRouteDto> pathVariableList,
        List<ApiRouteDto> wildcardList
    ) {
        static RouteHolder empty() {
            return new RouteHolder(new ConcurrentHashMap<>(), List.of(), List.of());
        }
    }

    // ================= INIT =================
    public static void init(List<ApiRouteDto> routes) {

        Map<String, ApiRouteDto> newExactMap       = new ConcurrentHashMap<>();
        List<ApiRouteDto>        newPathVariables   = new ArrayList<>();
        List<ApiRouteDto>        newWildcards       = new ArrayList<>();

        if (routes != null) {
            for (ApiRouteDto route : routes) {
                String path = normalize(route.getPath());
                route.setPath(path);

                if (hasPathVariable(path)) {
                    newPathVariables.add(route);
                } else if (isWildcard(path)) {
                    route.setPath(extractBasePath(path));
                    newWildcards.add(route);
                } else {
                    newExactMap.put(buildKey(path, route.getMethod()), route);
                }
            }

            // most specific match first (most literal segments wins)
            newPathVariables.sort((a, b) ->
                countLiteralSegments(b.getPath()) - countLiteralSegments(a.getPath())
            );
            // longest prefix first
            newWildcards.sort((a, b) ->
                b.getPath().length() - a.getPath().length()
            );
        }

        // Atomic swap — readers always see a consistent snapshot
        holder = new RouteHolder(newExactMap,
                                  Collections.unmodifiableList(newPathVariables),
                                  Collections.unmodifiableList(newWildcards));
    }

    // ================= GET =================
    public static ApiRouteDto get(String path, String method) {

        RouteHolder rd = holder;
        String normalizedPath = normalize(path);

        // 1. Exact match for the specific method  O(1)
        ApiRouteDto exact = rd.exactMap.get(buildKey(normalizedPath, method));
        // 1b. Fallback: any-method route (blank method = matches all HTTP methods)
        if (exact == null) {
            exact = rd.exactMap.get(buildKey(normalizedPath, ""));
        }
        if (exact != null) return exact;

        // 2. Path-variable match: /users/{id}, /items/{id}/details
        for (ApiRouteDto dto : rd.pathVariableList) {
            if (!methodMatches(dto.getMethod(), method)) continue;
            if (matchesPathVariable(dto.getPath(), normalizedPath)) return dto;
        }

        // 3. Wildcard match: /api/**
        for (ApiRouteDto dto : rd.wildcardList) {
            if (!methodMatches(dto.getMethod(), method)) continue;
            if (normalizedPath.equals(dto.getPath()) || normalizedPath.startsWith(dto.getPath() + "/")) {
                return dto;
            }
        }

        return null;
    }

    // ================= BULK READ =================

    /** Returns every cached route across all three buckets. */
    public static List<ApiRouteDto> getAllRoutes() {
        RouteHolder rd = holder;
        List<ApiRouteDto> all = new ArrayList<>();
        all.addAll(rd.exactMap.values());
        all.addAll(rd.pathVariableList);
        all.addAll(rd.wildcardList);
        return all;
    }

    /** Returns all routes whose groupCode matches (case-insensitive). */
    public static List<ApiRouteDto> getByGroupCode(String groupCode) {
        if (groupCode == null) return Collections.emptyList();
        return getAllRoutes().stream()
            .filter(r -> groupCode.equalsIgnoreCase(r.getGroupCode()))
            .toList();
    }

    // ================= GENERAL CACHE =================
    public static String getGeneral(String key) {
        return generalCaches.get(key);
    }

    public static void addGeneral(String key, String value) {
        generalCaches.put(key, value);
    }

    // ================= UTIL =================

    /**
     * Returns true when the route's stored method either matches the incoming
     * method or is blank/null (meaning the route accepts any HTTP method).
     */
    private static boolean methodMatches(String routeMethod, String incomingMethod) {
        return routeMethod == null || routeMethod.isEmpty() || routeMethod.equalsIgnoreCase(incomingMethod);
    }

    private static boolean hasPathVariable(String path) {
        return path != null && path.contains("{");
    }

    private static boolean isWildcard(String path) {
        return path.endsWith("/**");
    }

    private static String extractBasePath(String path) {
        return path.substring(0, path.length() - 3);
    }

    /** Null-safe: a null/blank method produces a key ending with ":" */
    private static String buildKey(String path, String method) {
        return path + ":" + (method != null ? method.toUpperCase() : "");
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Matches a request path against a path-variable pattern.
     * Segments in {curly braces} match any single segment.
     * A ** segment matches all remaining segments.
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
            if (!seg.isEmpty() && !seg.startsWith("{") && !"**".equals(seg)) count++;
        }
        return count;
    }
}