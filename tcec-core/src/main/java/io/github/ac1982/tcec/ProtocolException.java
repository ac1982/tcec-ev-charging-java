package io.github.ac1982.tcec;
/** Safe protocol failure. Messages deliberately contain no wire payloads or secrets. */
public class ProtocolException extends RuntimeException {
    public static final int INVALID_REQUEST = 4003;
    public static final int INVALID_SIGNATURE = 4001;
    public static final int INVALID_PAYLOAD = 4004;
    public static final int INVALID_TOKEN = 4002;
    public static final int SYSTEM_ERROR = 500;
    private final int ret;
    public ProtocolException(int ret, String message) { super(message); this.ret = ret; }
    public int ret() { return ret; }
}
