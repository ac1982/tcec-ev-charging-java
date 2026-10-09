package io.github.ac1982.tcec.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/** Receiver policy; disabled by default. Confirm timezone and replay window with each partner. */
@ConfigurationProperties("tcec.server")
public class TcecServerProperties {
    private boolean enabled;
    private String basePath = "";
    private Duration allowedClockSkew = Duration.ofMinutes(5);
    private ZoneId protocolZone = ZoneId.of("Asia/Shanghai");
    private Duration tokenTtl = Duration.ofHours(2);
    private int maxRequestBytes = 2 * 1024 * 1024;
    private int replayCapacity = 100_000;
    private int tokenCapacity = 100_000;
    private boolean requireSharedStores;

    public void validate() {
        if (basePath == null || !(basePath.isEmpty() || "/".equals(basePath)
                || (basePath.matches("(?:/[A-Za-z0-9_-]+)+") && !basePath.endsWith("/"))))
            throw new IllegalArgumentException("Base path must be empty, /, or an absolute path prefix");
        Objects.requireNonNull(protocolZone, "protocolZone");
        if (allowedClockSkew == null || allowedClockSkew.isNegative() || allowedClockSkew.isZero())
            throw new IllegalArgumentException("allowedClockSkew must be positive");
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero() || tokenTtl.getNano() != 0
                || tokenTtl.compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalArgumentException("tokenTtl must be whole seconds from 1 second to 7 days");
        if (maxRequestBytes < 1 || maxRequestBytes == Integer.MAX_VALUE)
            throw new IllegalArgumentException("maxRequestBytes out of range");
        if (tokenCapacity < 1) throw new IllegalArgumentException("tokenCapacity must be positive");
        if (replayCapacity < 1) throw new IllegalArgumentException("replayCapacity must be positive");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public Duration getAllowedClockSkew() { return allowedClockSkew; }
    public void setAllowedClockSkew(Duration allowedClockSkew) { this.allowedClockSkew = allowedClockSkew; }
    public ZoneId getProtocolZone() { return protocolZone; }
    public void setProtocolZone(ZoneId protocolZone) { this.protocolZone = protocolZone; }
    public Duration getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public void setMaxRequestBytes(int maxRequestBytes) { this.maxRequestBytes = maxRequestBytes; }
    public int getReplayCapacity() { return replayCapacity; }
    public void setReplayCapacity(int replayCapacity) { this.replayCapacity = replayCapacity; }
    public int getTokenCapacity() { return tokenCapacity; }
    public void setTokenCapacity(int tokenCapacity) { this.tokenCapacity = tokenCapacity; }
    /** Refuse the single-JVM defaults when a deployment requires shared stores. */
    public boolean isRequireSharedStores() { return requireSharedStores; }
    public void setRequireSharedStores(boolean requireSharedStores) { this.requireSharedStores = requireSharedStores; }
}
