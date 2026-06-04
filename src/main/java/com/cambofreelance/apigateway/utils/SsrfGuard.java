package com.cambofreelance.apigateway.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Guards against Server-Side Request Forgery by rejecting URIs that resolve
 * to loopback, private (RFC-1918), or link-local addresses.
 *
 * Call {@link #assertSafeUri(String)} before persisting any admin-supplied
 * upstream URI (OAuth2 introspection endpoints, service group URIs, etc.).
 */
public final class SsrfGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /** Hostnames that are always blocked regardless of DNS resolution. */
    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
        "localhost",
        "metadata.google.internal",   // GCP metadata service
        "instance-data",              // legacy GCP alias
        "169.254.169.254"             // AWS / Azure / GCP link-local metadata IP
    );

    private SsrfGuard() {}

    /**
     * Validates that {@code rawUri} is safe to use as an upstream target.
     *
     * @throws IllegalArgumentException describing the violation
     */
    public static void assertSafeUri(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            throw new IllegalArgumentException("URI must not be blank");
        }

        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URI: " + rawUri);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("URI scheme must be http or https, got: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URI must have a non-empty host");
        }

        if (BLOCKED_HOSTNAMES.contains(host.toLowerCase())) {
            throw new IllegalArgumentException("URI host is not permitted: " + host);
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("URI host cannot be resolved: " + host);
        }

        if (addr.isLoopbackAddress()) {
            throw new IllegalArgumentException("URI resolves to a loopback address: " + host);
        }
        if (addr.isSiteLocalAddress()) {
            throw new IllegalArgumentException("URI resolves to a private (RFC-1918) address: " + host);
        }
        if (addr.isLinkLocalAddress()) {
            throw new IllegalArgumentException("URI resolves to a link-local address (possible metadata service): " + host);
        }
        if (addr.isAnyLocalAddress()) {
            throw new IllegalArgumentException("URI resolves to a wildcard/any-local address: " + host);
        }
    }
}
