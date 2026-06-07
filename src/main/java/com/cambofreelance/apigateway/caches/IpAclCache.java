package com.cambofreelance.apigateway.caches;

import com.cambofreelance.apigateway.models.IpAccessControl;

import java.util.ArrayList;
import java.util.List;

public class IpAclCache {

    private static volatile List<IpAccessControl> rules = new ArrayList<>();

    public static void init(List<IpAccessControl> newRules) {
        rules = new ArrayList<>(newRules != null ? newRules : List.of());
    }

    public static List<IpAccessControl> getRules() {
        return rules;
    }
}