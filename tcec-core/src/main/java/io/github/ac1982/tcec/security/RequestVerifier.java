package io.github.ac1982.tcec.security;
import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.wire.RequestEnvelope;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Objects;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
/** Local hardening policy, not a claim that the standard fixes a timestamp acceptance window. */
public final class RequestVerifier {
    public static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private final Clock clock;
    private final ZoneId zone;
    private final Duration allowedSkew;
    private final ReplayStore replays;
    public RequestVerifier(Clock clock, ZoneId zone, Duration allowedSkew, ReplayStore replays) {
        this.clock = Objects.requireNonNull(clock); this.zone = Objects.requireNonNull(zone); this.replays = Objects.requireNonNull(replays);
        if (allowedSkew == null || allowedSkew.isNegative() || allowedSkew.isZero() || allowedSkew.compareTo(Duration.ofDays(1)) > 0) throw new IllegalArgumentException("Allowed skew must be positive and at most one day");
        this.allowedSkew = allowedSkew;
    }
    /** Call only after authenticating the request MAC, so attackers cannot poison replay state. */
    public void verify(RequestEnvelope envelope) {
        if (envelope == null || envelope.operatorId() == null || envelope.data() == null || envelope.sig() == null || envelope.timeStamp() == null || !envelope.timeStamp().matches("[0-9]{14}") || envelope.seq() == null || !envelope.seq().matches("[0-9]{4}")) throw invalid();
        final Instant timestamp;
        try { timestamp = LocalDateTime.parse(envelope.timeStamp(), TIMESTAMP).atZone(zone).toInstant(); }
        catch (DateTimeException e) { throw invalid(); }
        Instant now = clock.instant();
        if (timestamp.isBefore(now.minus(allowedSkew)) || timestamp.isAfter(now.plus(allowedSkew))) throw invalid();
        if (!replays.claim(envelope.operatorId(), envelope.timeStamp(), fingerprint(envelope), timestamp.plus(allowedSkew).plusSeconds(1))) throw invalid();
    }
    private static String fingerprint(RequestEnvelope envelope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Length framing avoids ambiguity; only already-authenticated envelope fields
            // participate. Distinct payloads may share the SDK's fixed Seq=0001.
            for (String field : new String[]{envelope.operatorId(), envelope.data(), envelope.timeStamp(), envelope.seq(), envelope.sig()}) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private static ProtocolException invalid() { return new ProtocolException(ProtocolException.INVALID_REQUEST, "Invalid or repeated request"); }
}
