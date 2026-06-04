package com.cambofreelance.apigateway.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Guards against Server-Side Request Forgery by rejecting URIs that resolve
 * to loopback, private (RFC-1918), link-local, or otherwise non-routable addresses.
 *
 * Defence strategy:
 *  1. Enforce http/https scheme only.
 *  2. Resolve ALL addresses for the hostname (not just the first) to defeat
 *     round-robin DNS that mixes safe and unsafe records.
 *  3. Unwrap IPv4-mapped IPv6 (::ffff:x.x.x.x) before classification so that
 *     tricks like "::ffff:127.0.0.1" are caught the same as "127.0.0.1".
 *  4. Block loopback, site-local (RFC-1918), link-local (169.254/fe80),
 *     any-local (0.0.0.0/::), and multicast.
 *
 * Limitation: DNS rebinding (safe IP at validation → private IP at request time)
 * requires a network-layer solution (egress firewall, iptables) in addition to
 * this check.
 *
 * Call {@link #assertSafeUri(String)} before persisting any admin-supplied
 * upstream URI (OAuth2 introspection endpoints, service group URIs, etc.).
 */
public final class SsrfGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private SsrfGuard() {}

    /**
     * Validates that {@code rawUri} is safe to use as an upstream target.
     *
     * @throws IllegalArgumentException describing the specific violation
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
            throw new IllegalArgumentException(
                "URI scheme must be http or https, got: " + scheme);
        }

        // URI.getHost() strips brackets from IPv6 literals ([::1] → ::1)
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URI must have a non-empty host");
        }

        // Resolve ALL addresses — defeats round-robin DNS mixing safe/unsafe records
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("URI host cannot be resolved: " + host);
        }

        for (InetAddress addr : addresses) {
            assertSafeAddress(host, addr);
        }
    }

    // ── Per-address classification ─────────────────────────────────────────────

    private static void assertSafeAddress(String host, InetAddress addr) {
        // Unwrap ::ffff:x.x.x.x before classification so IPv4-mapped IPv6 tricks
        // (e.g. "::ffff:127.0.0.1", "::ffff:192.168.1.1") are caught correctly.
        InetAddress effective = unwrapIpv4MappedIpv6(addr);

        if (effective.isLoopbackAddress()) {
            throw new IllegalArgumentException(
                "URI host resolves to a loopback address: " + host);
        }
        if (effective.isSiteLocalAddress()) {
            // Covers 10/8, 172.16/12, 192.168/16 (IPv4) and fc00::/7 (IPv6 ULA)
            throw new IllegalArgumentException(
                "URI host resolves to a private (RFC-1918) address: " + host);
        }
        if (effective.isLinkLocalAddress()) {
            // Covers 169.254/16 (AWS/GCP/Azure metadata) and fe80::/10 (IPv6)
            throw new IllegalArgumentException(
                "URI host resolves to a link-local address (possible cloud metadata service): " + host);
        }
        if (effective.isAnyLocalAddress()) {
            // Covers 0.0.0.0 and ::
            throw new IllegalArgumentException(
                "URI host resolves to a wildcard/any-local address: " + host);
        }
        if (effective.isMulticastAddress()) {
            throw new IllegalArgumentException(
                "URI host resolves to a multicast address: " + host);
        }
    }

    /**
     * Detects IPv4-mapped IPv6 addresses (::ffff:x.x.x.x) and returns the
     * underlying IPv4 InetAddress so that site-local and loopback checks work
     * correctly. Returns the original address unchanged for all other cases.
     *
     * Format: 10 zero bytes | 0xff 0xff | 4 IPv4 bytes (RFC 4291 §2.5.5.2)
     */
    private static InetAddress unwrapIpv4MappedIpv6(InetAddress addr) {
        byte[] raw = addr.getAddress();
        if (raw.length != 16) return addr;

        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) return addr;
        }
        if (raw[10] != (byte) 0xff || raw[11] != (byte) 0xff) return addr;

        try {
            return InetAddress.getByAddress(new byte[]{raw[12], raw[13], raw[14], raw[15]});
        } catch (UnknownHostException e) {
            return addr;
        }
    }
}
