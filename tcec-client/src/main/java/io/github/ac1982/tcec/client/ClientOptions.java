package io.github.ac1982.tcec.client;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/** Transport limits. Plain HTTP is an explicit test-only exception for loopback hosts. */
public record ClientOptions(Duration connectTimeout, Duration requestTimeout, int maxResponseBytes,
                            ZoneId protocolZone, boolean allowHttpLoopback) {
    public ClientOptions {
        positive(connectTimeout, "connectTimeout");
        positive(requestTimeout, "requestTimeout");
        if (maxResponseBytes < 1) throw new IllegalArgumentException("maxResponseBytes must be positive");
        Objects.requireNonNull(protocolZone, "protocolZone");
    }

    public static ClientOptions defaults() {
        return new ClientOptions(Duration.ofSeconds(10), Duration.ofSeconds(30),
                2 * 1024 * 1024, ZoneId.of("Asia/Shanghai"), false);
    }

    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative() || value.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException(name + " must be positive and at most one day");
    }
}
