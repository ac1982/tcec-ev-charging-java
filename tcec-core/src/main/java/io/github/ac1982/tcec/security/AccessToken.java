package io.github.ac1982.tcec.security;
import java.time.Instant;
import java.util.Objects;
public record AccessToken(String value, Instant expiresAt) {
    public AccessToken { if (value == null || value.isBlank()) throw new IllegalArgumentException("Token required"); Objects.requireNonNull(expiresAt); }
    @Override public String toString() { return "AccessToken[redacted]"; }
}
