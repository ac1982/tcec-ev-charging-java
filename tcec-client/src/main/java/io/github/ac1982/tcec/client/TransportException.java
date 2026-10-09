package io.github.ac1982.tcec.client;

/** Transport failure; never includes response bodies, tokens, keys, or request payloads. */
public final class TransportException extends RuntimeException {
    private final int statusCode;

    public TransportException(String message) {
        this(message, 0);
    }

    public TransportException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /** HTTP status, or zero when no usable HTTP response was received. */
    public int statusCode() { return statusCode; }
}
