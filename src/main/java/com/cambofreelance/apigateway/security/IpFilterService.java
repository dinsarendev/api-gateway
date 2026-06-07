package com.cambofreelance.apigateway.security;

import com.cambofreelance.apigateway.caches.IpAclCache;
import com.cambofreelance.apigateway.dto.ApiRouteDto;
import com.cambofreelance.apigateway.models.IpAccessControl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;

@Slf4j
@Service
public class IpFilterService {

    /**
     * Returns true if the client IP is allowed to access the route.
     * Order: blacklist wins → whitelist (if any rules) → allow all.
     */
    public Mono<Boolean> isAllowed(String clientIp, ApiRouteDto route) {
        return Mono.fromCallable(() -> check(clientIp, route));
    }

    private boolean check(String clientIp, ApiRouteDto route) {
        List<IpAccessControl> all = IpAclCache.getRules();
        if (all.isEmpty()) return true;

        String groupCode = route.getGroupCode();
        String routeId   = route.getId() != null ? route.getId().toString() : "";

        List<IpAccessControl> applicable = all.stream()
            .filter(r -> "GLOBAL".equals(r.getScope())
                || ("GROUP".equals(r.getScope()) && groupCode != null && groupCode.equals(r.getScopeId()))
                || ("ROUTE".equals(r.getScope()) && routeId.equals(r.getScopeId())))
            .toList();

        // Blacklist has priority
        boolean blacklisted = applicable.stream()
            .filter(r -> "BLACKLIST".equals(r.getType()))
            .anyMatch(r -> matchesCidr(clientIp, r.getIpCidr()));
        if (blacklisted) return false;

        // Whitelist: only enforce if at least one whitelist rule exists in this context
        List<IpAccessControl> whitelistRules = applicable.stream()
            .filter(r -> "WHITELIST".equals(r.getType()))
            .toList();
        if (!whitelistRules.isEmpty()) {
            return whitelistRules.stream().anyMatch(r -> matchesCidr(clientIp, r.getIpCidr()));
        }

        return true;
    }

    private boolean matchesCidr(String clientIp, String cidr) {
        if (cidr == null || clientIp == null) return false;
        try {
            if (!cidr.contains("/")) {
                return clientIp.equals(cidr);
            }
            String[] parts    = cidr.split("/", 2);
            int prefixLength  = Integer.parseInt(parts[1]);
            InetAddress network = InetAddress.getByName(parts[0]);
            InetAddress client  = InetAddress.getByName(clientIp);
            byte[] netBytes     = network.getAddress();
            byte[] clientBytes  = client.getAddress();
            if (netBytes.length != clientBytes.length) return false;
            int mask       = prefixLength == 0 ? 0 : (-1 << (32 - prefixLength));
            int networkInt = ByteBuffer.wrap(netBytes).getInt() & mask;
            int clientInt  = ByteBuffer.wrap(clientBytes).getInt() & mask;
            return networkInt == clientInt;
        } catch (Exception e) {
            log.warn("Invalid CIDR rule '{}': {}", cidr, e.getMessage());
            return false;
        }
    }
}