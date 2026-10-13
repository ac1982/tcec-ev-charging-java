package io.github.ac1982.tcec.security;
import java.time.Instant;
/**
 * Implement atomically across all instances in a cluster. False means replay or
 * capacity refusal. The third argument is an opaque authenticated-message
 * fingerprint, not the four-character protocol Seq. Never include an HTTP route:
 * the request signature does not authenticate the route.
 */
@FunctionalInterface
public interface ReplayStore {
    boolean claim(String operatorId, String timeStamp, String requestFingerprint, Instant expiresAt);

    /**
     * Releases a claim this process admitted, after the business handler failed, so the partner can
     * retry the identical signed request. Called only for a claim that returned true. The default
     * keeps the claim (1.0.0 behavior). Implementations must not throw for an absent claim.
     * @since 1.1.0
     */
    default void release(String operatorId, String timeStamp, String requestFingerprint) { }
}
