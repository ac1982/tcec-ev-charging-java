package io.github.ac1982.tcec.protocol;
import java.util.Objects;

/** A typed endpoint with a stable name and an explicit destination path. */
public record Endpoint<Q, R>(String path, Class<Q> requestType, Class<R> responseType,
                             boolean tokenRequired, String outboundPath) {
    public Endpoint {
        if (path == null || !path.matches("[a-zA-Z][a-zA-Z0-9_]*"))
            throw new IllegalArgumentException("Endpoint name must be one path segment");
        Objects.requireNonNull(requestType);
        Objects.requireNonNull(responseType);
        // Only clean path segments are allowed: no authorities, query, fragments or traversal.
        if (outboundPath == null || !outboundPath.matches("/?[a-zA-Z][a-zA-Z0-9_]*(/[a-zA-Z][a-zA-Z0-9_]*)*"))
            throw new IllegalArgumentException("Endpoint destination must be a clean absolute or relative path");
    }
    /** A custom endpoint relative to the client base URI. */
    public Endpoint(String path, Class<Q> requestType, Class<R> responseType, boolean tokenRequired) {
        this(path, requestType, responseType, tokenRequired, path);
    }
}
