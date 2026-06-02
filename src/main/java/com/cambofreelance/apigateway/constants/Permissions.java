package com.cambofreelance.apigateway.constants;

public final class Permissions {

    private Permissions() {}

    // ── Routes ────────────────────────────────────────────────────────────────
    public static final String ROUTE_READ    = "ROUTE_READ";
    public static final String ROUTE_WRITE   = "ROUTE_WRITE";

    // ── Groups ────────────────────────────────────────────────────────────────
    public static final String GROUP_READ    = "GROUP_READ";
    public static final String GROUP_WRITE   = "GROUP_WRITE";

    // ── Service registry / health ─────────────────────────────────────────────
    public static final String REGISTRY_READ  = "REGISTRY_READ";
    public static final String REGISTRY_WRITE = "REGISTRY_WRITE";
    public static final String HEALTH_READ    = "HEALTH_READ";

    // ── Security (API keys, OAuth2, IP ACL) ──────────────────────────────────
    public static final String SECURITY_READ  = "SECURITY_READ";
    public static final String SECURITY_WRITE = "SECURITY_WRITE";

    // ── Users ─────────────────────────────────────────────────────────────────
    public static final String USER_READ  = "USER_READ";
    public static final String USER_WRITE = "USER_WRITE";

    // ── Roles & Permissions ───────────────────────────────────────────────────
    public static final String ROLE_READ  = "ROLE_READ";
    public static final String ROLE_WRITE = "ROLE_WRITE";
}
