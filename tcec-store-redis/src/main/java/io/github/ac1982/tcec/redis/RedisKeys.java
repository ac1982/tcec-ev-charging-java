package io.github.ac1982.tcec.redis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Internal key encoding: bounded inputs, domain separation, and unambiguous framing. */
final class RedisKeys {
    private RedisKeys() { }

    static String namespace(String namespace) {
        if (namespace == null || !namespace.matches("[A-Za-z0-9:_-]{1,64}")) {
            throw new IllegalArgumentException("Redis namespace must contain 1 to 64 safe ASCII characters");
        }
        return namespace;
    }

    static boolean bounded(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    static String key(String namespace, String kind, String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return namespace + ':' + kind + ':' + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform");
        }
    }
}
