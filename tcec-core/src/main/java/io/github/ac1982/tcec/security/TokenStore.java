package io.github.ac1982.tcec.security;

import java.time.Duration;

/**
 * Storage for tokens issued to authenticated inbound partners. Clustered services must share one
 * implementation across nodes; the default in-memory implementation is single-JVM only.
 * Implementations must atomically reuse a live token or create one, enforce expiration, and fail
 * closed when unavailable. Returned expiry is authoritative and must not be extended on cache hits.
 * Namespace a shared implementation by local service/credential direction as well as operator ID.
 */
public interface TokenStore {
    AccessToken issue(String operatorId, Duration ttl);
    boolean validate(String operatorId, String token);
}
