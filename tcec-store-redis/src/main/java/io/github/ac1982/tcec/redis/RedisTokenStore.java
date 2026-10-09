package io.github.ac1982.tcec.redis;

import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.security.AccessToken;
import io.github.ac1982.tcec.security.TokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Shared tokens, atomically issued/reused without extending their expiry.
 * The caller owns the pooled Redis client and its lifecycle. Redis server time is authoritative.
 * Use a different namespace for each local service and credential direction.
 */
public final class RedisTokenStore implements TokenStore {
    private static final long MAX_TTL_SECONDS = 604800;
    private static final String ISSUE = """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            if redis.call('EXISTS', KEYS[1]) == 1 then
                local token = redis.call('HGET', KEYS[1], 'token')
                local expiry = redis.call('HGET', KEYS[1], 'expires')
                if not token or not expiry or not tonumber(expiry) or redis.call('PTTL', KEYS[1]) < 0 then
                    return redis.error_reply('Invalid token state')
                end
                if tonumber(expiry) > now then
                    return {token, expiry}
                end
                redis.call('DEL', KEYS[1])
            end
            local expiry = string.format('%.0f', now + tonumber(ARGV[2]))
            redis.call('HSET', KEYS[1], 'token', ARGV[1], 'expires', expiry)
            redis.call('PEXPIREAT', KEYS[1], expiry)
            return {ARGV[1], expiry}
            """;
    private static final String VALIDATE = """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local expiry = redis.call('HGET', KEYS[1], 'expires')
            if not expiry or not tonumber(expiry) or tonumber(expiry) <= now or redis.call('PTTL', KEYS[1]) <= 0 then
                return ''
            end
            return redis.call('HGET', KEYS[1], 'token') or ''
            """;
    private final RedisClient redis;
    private final String namespace;
    private final SecureRandom random = new SecureRandom();

    public RedisTokenStore(RedisClient redis, String namespace) {
        this.redis = Objects.requireNonNull(redis, "Redis client is required");
        this.namespace = RedisKeys.namespace(namespace);
    }

    @Override
    public AccessToken issue(String operatorId, Duration ttl) {
        if (!RedisKeys.bounded(operatorId, 256) || ttl == null || ttl.getNano() != 0
                || ttl.getSeconds() < 1 || ttl.getSeconds() > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("Partner and whole-second token TTL of 1 to 604800 seconds required");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String candidate = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            Object result = redis.eval(ISSUE, List.of(key(operatorId)), List.of(candidate, Long.toString(ttl.toMillis())));
            if (!(result instanceof List<?> values) || values.size() != 2
                    || !(values.get(0) instanceof String token) || !RedisKeys.bounded(token, 4096)
                    || !(values.get(1) instanceof String expiry)) {
                throw unavailable();
            }
            return new AccessToken(token, Instant.ofEpochMilli(Long.parseLong(expiry)));
        } catch (JedisException | NumberFormatException failure) {
            // Redis exceptions may contain credentials, addresses, or command arguments. Do not retain them.
            throw unavailable();
        }
    }

    @Override
    public boolean validate(String operatorId, String token) {
        if (!RedisKeys.bounded(operatorId, 256) || !RedisKeys.bounded(token, 4096)) return false;
        try {
            Object result = redis.eval(VALIDATE, List.of(key(operatorId)), List.of());
            return result instanceof String expected && RedisKeys.bounded(expected, 4096)
                    && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        } catch (JedisException failure) {
            return false;
        }
    }

    private String key(String operatorId) { return RedisKeys.key(namespace, "token", operatorId); }
    private static ProtocolException unavailable() {
        return new ProtocolException(ProtocolException.SYSTEM_ERROR, "Token store unavailable");
    }
}
