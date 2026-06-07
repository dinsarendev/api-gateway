package com.cambofreelance.apigateway.caches;

import com.cambofreelance.apigateway.models.ServiceNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceInstanceCache {

    // service_id (uppercase) → list of UP instances
    private static Map<String, List<ServiceNode>> instanceMap;

    // ================= INIT =================
    /** Initialises the cache from all ACT nodes. Only UP nodes are served to the load balancer. */
    public static void init(List<ServiceNode> nodes) {
        Map<String, List<ServiceNode>> map = new ConcurrentHashMap<>();

        for (ServiceNode node : nodes) {
            String key = normalizeId(node.getServiceId());
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }

        instanceMap = map;
    }

    // ================= GET =================
    public static List<ServiceNode> getInstances(String serviceId) {
        if (instanceMap == null) return Collections.emptyList();
        return instanceMap.getOrDefault(normalizeId(serviceId), Collections.emptyList());
    }

    public static List<String> getServiceIds() {
        if (instanceMap == null) return Collections.emptyList();
        return new ArrayList<>(instanceMap.keySet());
    }

    // ================= UTIL =================
    private static String normalizeId(String serviceId) {
        return serviceId == null ? "" : serviceId.toUpperCase();
    }
}
